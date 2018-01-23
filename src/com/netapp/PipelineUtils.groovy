package com.netapp

def login(String apiURL, String authToken) {
    print ${apiUrl}
    sh """
        set -x
        oc login --token=${authToken} ${apiURL} >/dev/null 2>&1 || echo 'OpenShift login failed'
    """
    print "Login successful"
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

def processOcpTemplates(String templatePath, String parameters, String project) {

    print "Process OpenShift templates..."
    sh """
        oc process -f ${templatePath} ${parameters} -n ${project} | oc apply -f - -n ${project}
    """
}

def startOcpBuild(String project, String buildConfigName) {

    print "Building in OpenShift..."
    sh """
       oc start-build ${buildConfigName} --follow -n ${project}
    """
    //openshiftBuild bldCfg: ${buildConfigName}, showBuildLogs: 'true', waitTime: '15', waitUnit: 'min'
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
