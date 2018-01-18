package com.fismobile.mplat

def login(String apiURL, String authToken) {
    sh """
        set +x
        oc login --token=${authToken} ${apiURL} --insecure-skip-tls-verify >/dev/null 2>&1 || echo 'OpenShift login failed'
    """
}

/**
   * Runs mvn clean package on application code
   */
def unitTestAndPackageJar(String mavenCredentialsId, String pomPath, String mavenArgs) {
    stage('Unit Test & Package Jar'){
        print "Packaging Jar..."
        withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: mavenCredentialsId, passwordVariable: 'pass', usernameVariable: 'user']]) {
            sh """
                mvn -f ${pomPath} ${mavenArgs} -U \
                    -DartifactoryUser=${user} -DartifactoryPassword=${pass} clean package
            """
        }
    }
}

def processTemplateAndStartBuild(String templatePath, String parameters, String project, String buildConfigName, String jarPath) {
    stage("OCP Build"){
        print "Building in OpenShift..."
        sh """
            oc process -f ${templatePath} ${parameters} -n ${project} | oc apply -f - -n ${project}
            oc start-build ${buildConfigName} --from-file=${jarPath} --follow -n ${project}
        """
    }
}

/**
   * Runs API tests with mvn verify command
   */
def apiTest(String mavenCredentialsId, String mavenArgs, String microservice, String project) {

    stage('API Tests'){
        withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: mavenCredentialsId, passwordVariable: 'pass', usernameVariable: 'user']]) {
            sh """
                mvn -f app-root/pom.xml ${mavenArgs} -DartifactoryUser=${user} -DartifactoryPassword=${pass} \
                    -Ddsm.url=https://${microservice}-${project}.snapshot.mservices.fismobile.net verify
            """
        }
        // Capture and aggregate JUnit reports from the API tests.
        junit '**/surefire-reports/*.xml,**/failsafe-reports/*.xml'
    }

}

/**
   * Spins up JMeter slave pods and runs Distrubuted Stress tests
   * Must run on jenkins instance with jmeter binaries and JMETER_HOME set
   */
def stressTest(String microservice, int slaveReplicas, String jenkinsProject, String ocpUrl,
    String jenkinsToken, String stressTestProject, String testFilePath, String propertiesFilePath) {

    stage('Stress Test') {
        sh """
            oc process -f mplat-openshift/mplat-openshift-system/openshift/templates/mplat-jmeter-slave-template.yml \
                APPLICATION_NAME=jmeter-slave-${microservice} REPLICAS=${slaveReplicas} -n ${jenkinsProject} | oc apply -f - -n ${jenkinsProject}
        """

        try {
            openshiftVerifyDeployment(
                depCfg: "jmeter-slave-${microservice}",
                namespace: jenkinsProject,
                replicaCount: slaveReplicas,
                apiURL: ocpUrl,
                authToken: jenkinsToken
            )
        } catch(error) {
            // Deployment fails, be sure to delete the jmeter slave DC
            sh """
                oc delete all -l app=jmeter-slave-${microservice} -n ${jenkinsProject}
                oc delete pod jmeter-slave-mplat-reference-1-deploy --ignore-not-found=true -n cicd
            """
            throw error
        }


        pods = sh (
            script: """
                oc get pods -n ${jenkinsProject} | grep jmeter-slave-${microservice} | awk '{print \$1}'
            """,
            returnStdout: true
        ).trim()
        podList = pods.split("\n")

        ips = ""
        // Loop through the pods and collect their IPS
        for (int i = 0; i < podList.size(); i++) {
            pod = podList[i]
            ip = sh (
                script: """
                    oc describe pod ${pod} -n ${jenkinsProject} | grep IP | awk '{print \$2}'
                """,
                returnStdout: true
            ).trim()
            ips = ips + ip + ","
        }
        // Get rid of that last comma
        ips = ips.substring(0, ips.length()-1)

        sh """
            ${JMETER_HOME}/bin/jmeter -n -t "${testFilePath}" \
                -p "${propertiesFilePath}" -j /dev/stdout \
                -Ghostname=${microservice}-${stressTestProject}.snapshot.mservices.fismobile.net \
                -Gthreads=50 -Gcount=50 \
                -R ${ips} \
                -l "app-root/jmeter/results.jtl"
            oc delete all -l app=jmeter-slave-${microservice} -n ${jenkinsProject}
            oc delete pod jmeter-slave-mplat-reference-1-deploy --ignore-not-found=true -n cicd
        """

        performanceReport compareBuildPrevious: false,
            configType: 'ART',
            errorFailedThreshold: 0,
            errorUnstableResponseTimeThreshold: '',
            errorUnstableThreshold: 0,
            failBuildIfNoResultFile: false,
            ignoreFailedBuild: false,
            ignoreUnstableBuild: true,
            modeOfThreshold: false,
            modePerformancePerTestCase: true,
            modeThroughput: true,
            nthBuildNumber: 0,
            parsers: [[$class: 'JMeterParser', glob: 'app-root/jmeter/results.jtl']],
            relativeFailedThresholdNegative: 0,
            relativeFailedThresholdPositive: 0,
            relativeUnstableThresholdNegative: 0,
            relativeUnstableThresholdPositive: 0
    }
}

def sonar(String mavenCredentialsId, String pomPath, String mavenArgs, String profile,
    String branch, String sonarUrl, String sonarCredentialsId, boolean breakBuild){

    stage('Sonar'){
        withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: mavenCredentialsId, passwordVariable: 'pass', usernameVariable: 'user']]) {
            sh """
                mvn -f ${pomPath} ${mavenArgs} \
                    -DartifactoryUser=${user} -DartifactoryPassword=${pass} \
                    clean install sonar:sonar -DskipITs -Psonar,artifactory,metrics \
                    -Dsonar.profile="${profile}" -Dsonar.branch="${branch}"
            """
        }

        pom = readMavenPom file: pomPath
        projectKey = "${pom.groupId}:${pom.artifactId}:${branch}"
        qualityGateStatus = processSonarQubeResults(sonarUrl, projectKey, sonarCredentialsId)

        if (!qualityGateStatus.contains("OK")) {
            if (breakBuild) {
                error("SonarQube quality gates failed")
            } else {
                print "Setting build status to unstable due SonarQube Quality Gate: ${qualityGateStatus}"
                currentBuild.result='UNSTABLE'
            }
        }
    }
}

/**
 * Parses logs for the SonarQube Urls, polls for sonar processing to finish
 * and returns Quality Gate Status [OK, WARN, ERROR, NONE]
 * Should be called directly after sonar stage
 */
def processSonarQubeResults(String sonarUrl, String projectKey, String sonarCredentialsId) {

    String sonarQubeTaskURL = null;
    String reportURL = null;

    // Get the most recent 150 log lines in a list
    def list = currentBuild.rawBuild.getLog(200)

    String taskLinePrefix = "[INFO] More about the report processing at "
    String reportLinePrefix = "[INFO] ANALYSIS SUCCESSFUL, you can browse "

    for (int i=list.size()-1; i>0; i--) {
        String line = list[i]
        if (line.contains(taskLinePrefix)) {
            sonarQubeTaskURL = line.substring(taskLinePrefix.length())
        }
        if (line.contains(reportLinePrefix)) {
            reportURL = line.substring(reportLinePrefix.length())
        }
    }

    if (sonarQubeTaskURL == null) {
        print "sonarQubeTaskURL url not found"
        return
    }

    print "SonarQube analysis report can be found at: " + reportURL

    String status = getSonarQubeTaskStatus(sonarQubeTaskURL)

    while (status == "PENDING" || status == "IN_PROGRESS") {
        sleep 1
        status = getSonarQubeTaskStatus(sonarQubeTaskURL)
    }

    if (status == "FAILED" || status == "CANCELED") {
        print "SonarQube analysis failed, please see to the SonarQube analysis: ${reportURL}"
        return "NONE"
    }

    qualityGateUrl = "${sonarUrl}/api/qualitygates/project_status?projectKey=${projectKey}"
    withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: sonarCredentialsId, passwordVariable: 'pass', usernameVariable: 'user']]) {
        output = sh (
            script:"""
              curl -sk -u "${user}":"${pass}" ${qualityGateUrl}
            """,
            returnStdout: true
        )
        jsonOutput = readJSON text: output
        status = jsonOutput["projectStatus"]["status"]
        print "SonarQube status is " + status
        return status
    }
}

/**
 * Call into SonarQube and to get status of a project analysis
 * Could return PENDING, IN_PROGRESS FAILED, CANCELED, SUCCESS
 */
def getSonarQubeTaskStatus(String statusUrl) {
    print "Requesting SonarQube status at " + statusUrl

    output = sh (
        script:"""
          curl -sk ${statusUrl}
        """,
        returnStdout: true
    )

    jsonOutput = readJSON text: output
    taskStatus = jsonOutput["task"]["status"]
    print "SonarQube task status is " + taskStatus
    return taskStatus
}

/**
 * Dowloads the distribution zip and then applys the ocp objects in the folder named the same as the project to be deployed into
 */
def downloadDeploymentAndDeploy(String microservice, String project, String ocpUrl, String authToken,
    String artifactoryCredentialsId, String distribuitionZipUrl, String registryUrl, String imageTag, String additionalArgs) {

    // stage("Deploy in ${project}"){
    //     withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: artifactoryCredentialsId, passwordVariable: 'pass', usernameVariable: 'user']]) {
    //         print 'Downloading distribution.zip for deployment'
    //         sh """
    //             curl ${additionalArgs} --user "${user}":"${pass}" -L -o distro.zip ${distribuitionZipUrl}
    //             unzip -o distro.zip
    //         """
    //     }
    //
    //     sh """
    //         oc apply -f openshift/config-maps/${project}/ -R -n ${project}
    //         #oc apply -f openshift/deployment.yml -n ${project}
    //     """
    //
    //     triggerDeploymentAndVerify(authToken, microservice, project, ocpUrl, '1')
    // }
    downloadDeploymentAndDeploy(microservice, project, project, ocpUrl, authToken,
        artifactoryCredentialsId, distribuitionZipUrl, registryUrl, imageTag, additionalArgs)
}

/**
 * Dowloads the distribution zip and then applys the ocp objects in the folder named the same as the project to be deployed into
 */
def downloadDeploymentAndDeploy(String microservice, String project, String projectFolder, String ocpUrl, String authToken,
    String artifactoryCredentialsId, String distribuitionZipUrl, String artifactoryUrl, String imageTag, String additionalArgs) {

    stage("Deploy in ${project}"){
        withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: artifactoryCredentialsId, passwordVariable: 'pass', usernameVariable: 'user']]) {
            print 'Downloading distribution.zip for deployment'
            sh """
                curl ${additionalArgs} --user "${user}":"${pass}" -L -o distro.zip ${distribuitionZipUrl}
                unzip -o distro.zip
            """
        }

        sh """
            oc apply -f openshift/config-maps/${projectFolder}/ -R -n ${project}
            #oc apply -f openshift/deployment.yml -n ${project}
        """

        triggerDeploymentAndVerify(authToken, microservice, project, ocpUrl, '1')
    }
}

def triggerDeploymentAndVerify(String authToken, String microservice, String project,
    String ocpUrl, String replicaCount) {

    openshiftDeploy(
        depCfg: microservice,
        namespace: project,
        apiURL: ocpUrl,
        authToken: authToken
    )

    openshiftVerifyDeployment(
        depCfg: microservice,
        namespace: project,
        replicaCount: replicaCount,
        apiURL: ocpUrl,
        authToken: authToken
    )
}

def downloadDeploymentAndABDeploy(String microservice, String project, String ocpUrl, String authToken,
    String artifactoryCredentialsId, String distribuitionZipUrl, String registryUrl, String imageTag, String additionalArgs) {

    stage("A/B Deploy in ${project}"){
        // Download distritbution zip
        withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: artifactoryCredentialsId, passwordVariable: 'pass', usernameVariable: 'user']]) {
            print 'Downloading distribution.zip for deployment'
            sh """
                curl ${additionalArgs} --user "${user}":"${pass}" -L -o distro.zip ${distribuitionZipUrl}
                unzip -o distro.zip
            """
        }

        // Deploy Green on side
        sh """
            cat openshift/config-maps/${project}/config.yml | sed -e "s/name: ${microservice}/name: ${microservice}-green/" | oc apply -f - -n ${project}

            oc process -f openshift/templates/mplat-dsm-deploy-service-template.yml APPLICATION_NAME=${microservice}-green \
                IMAGE=${registryUrl}/${microservice}:${imageTag} -n ${project} | oc apply -f - -n ${project}
        """

        triggerDeploymentAndVerify(authToken, microservice + "-green", project, ocpUrl, '1')

        input 'Begin A/B Testing?'

        try {
            // Deploy split route
            sh """
                oc process -f openshift/templates/mplat-dsm-route-split-template.yml APPLICATION_NAME=${microservice} \
                    MAJOR_SERVICE_NAME=${microservice} MINOR_SERVICE_NAME=${microservice}-green -n ${project} | oc apply -f - -n ${project}
            """

            input 'Increase percentages to 50/50?'
            sh """
                oc patch route ${microservice} -p '{"spec":{"to":{"kind": "Service","name": "${microservice}","weight": 50},"alternateBackends": [{"kind": "Service","name": "${microservice}-green","weight": 50}]}}' -n ${project}
            """

            input 'Increase percentages to 0/100?'
            sh """
                oc patch route ${microservice} -p '{"spec":{"to":{"kind": "Service","name": "${microservice}","weight": 0},"alternateBackends": [{"kind": "Service","name": "${microservice}-green","weight": 100}]}}' -n ${project}
            """

            input 'Rollout?'

            // Now to do the roll out, first updating the dc that is receiving no traffic with the correct image
            sh """
                oc apply -f openshift/config-maps/${project}/config.yml -n ${project}

                oc process -f openshift/templates/mplat-dsm-deploy-service-template.yml APPLICATION_NAME=${microservice} \
                    IMAGE=${registryUrl}/${microservice}:${imageTag} -n ${project} | oc apply -f - -n ${project}
            """
            triggerDeploymentAndVerify(authToken, microservice, project, ocpUrl, '1')

            aborted = false

        } catch(err) {

            aborted = true

        } finally {
            // If any of the above steps fail we want to switch the route back to the "blue" deployment
            sh """
                oc process -f openshift/templates/mplat-dsm-route-template.yml \
                    APPLICATION_NAME=${microservice} -n ${project} | oc apply -f - -n ${project}

                oc delete svc ${microservice}-green -n ${project}
                oc delete dc ${microservice}-green -n ${project}
                oc delete configmap ${microservice}-green -n ${project}
            """
            if(aborted){
                error("A/B Testing Aborted")
            }

        }
    }
}
