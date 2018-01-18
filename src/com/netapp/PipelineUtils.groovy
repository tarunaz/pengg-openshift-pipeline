package com.netapp

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

def processTemplateAndStartBuild(String templatePath, String parameters, String project, String buildConfigName) {
    stage("OCP Build"){
        print "Building in OpenShift..."
        sh """
            oc process -f ${templatePath} ${parameters} -n ${project} | oc apply -f - -n ${project}
            //oc start-build ${buildConfigName} --follow -n ${project}
        """
        openshiftBuild bldCfg: ${buildConfigName}, showBuildLogs: 'true', waitTime: '15', waitUnit: 'min'
    }
}


def triggerDeploymentAndVerify(String microservice, String project,
    String ocpUrl, String replicaCount) {

    openshiftDeploy(
        depCfg: microservice,
        namespace: project,
        apiURL: ocpUrl
    )

    openshiftVerifyDeployment(
        depCfg: microservice,
        namespace: project,
        replicaCount: replicaCount,
        apiURL: ocpUrl
    )
}
