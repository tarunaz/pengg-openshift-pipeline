#!/usr/bin/groovy

import com.netapp.PipelineUtils

/**
   * Main Netapp Pipeline meant for development branch
   * @param gitRepoUrl URL to the DSM source code
   * @param microserviceSubmodule Submodule that gets built into jar
   * @param hipchatRooms Comma delimmited hipchat rooms
   * @param microservice DSM name
   * @param mavenCredentialsId Credential Id to user/pass that works with FIS artifactory in maven server
   * @param fortifyCredentialsId Credential Id to Fortify auth token
   * @param artifactoryCredentialsId Credential Id to push/pull artifacts from artifactory
   * @param artifactoryRepoBaseURL Artifactory URL for push/pull of artifacts
   * @param artifactoryRepoName Artifactory repo name
   */
def call(body) {

    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    // Instantiate variables used in pipeline
    ocpUrl = "tpaas-console.netapp.com"
    jenkinsToken = ""
  
    registryUrl = 'mobile-docker-1.repos.fismobile.com/test'
    def pipelineUtils = new PipelineUtils()

    try {
        node('nodejs') {
            // Clean workspace before doing anything
            deleteDir()

            // Skip TLS for Openshift Jenkins Plugin
            env.SKIP_TLS = 'true'

            jenkinsToken = readFile('/var/run/secrets/kubernetes.io/serviceaccount/token').trim()

	    echo "openshiftbuild  Connect & Trigger openshift Buid in registry cluster..."
       	    stage('build spog') {
		// Checkout openshift template files
                checkout([$class: 'GitSCM', branches: [[name: config.templateGitTag]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'netapp-openshift']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'a16fe3a9-b41b-4fe8-8f1a-55ae9607ad40', url: 'https://bitbucket.mfoundry.net/scm/~andrew.sandstrom/netapp-openshift.git']]])

  
		openshiftBuild bldCfg: 'spog-dev1', showBuildLogs: 'true', waitTime: '15', waitUnit: 'min'
    	    }

            stage('Checkout Source Code'){
                // Checkout source code
                def scmVars = checkout([$class: 'GitSCM', branches: [[name: config.developmentBranch]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'app-root']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'a16fe3a9-b41b-4fe8-8f1a-55ae9607ad40', url: config.gitRepoUrl]]])
                gitCommit = scmVars.GIT_COMMIT.take(7)
                print "gitCommit: ${gitCommit}"
                // Checkout template files (and maven settings file)
                // TODO this settings file should be in a diff repo
                // Using tags here for the template files for versioning
                checkout([$class: 'GitSCM', branches: [[name: config.templateGitTag]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'mplat-openshift']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'a16fe3a9-b41b-4fe8-8f1a-55ae9607ad40', url: 'https://bitbucket.mfoundry.net/scm/~andrew.sandstrom/mplat-openshift.git']]])

                // Process the pom for correct version number
                pom = readMavenPom file: 'app-root/pom.xml'
                revision = pom.properties.revision
                // Using the base version + git commit to be the image tag as well as the version that will go on the
                // artifacts built by maven
                tag = revision + "-" + gitCommit
                mavenArgs = baseMavenArgs + " -Dchangelist= -Dsha1=-${gitCommit}"
            }

           
            pipelineUtils.unitTestAndPackageJar(config.mavenCredentialsId, "app-root/pom.xml", mavenArgs)

            pom = readMavenPom file: "app-root/${config.microserviceSubmodule}/pom.xml"
            pipelineUtils.processTemplateAndStartBuild("mplat-openshift/mplat-openshift-system/openshift/templates/mplat-dsm-build-internal-registry-template.yml",
                "APPLICATION_NAME=${config.microservice} OUTPUT_IMAGE_TAG=${tag}", "dsm-dev", config.microservice, "app-root/${config.microserviceSubmodule}/target/${pom.artifactId}-${tag}.jar")

            sh """
                oc apply -f app-root/openshift/config-maps/dsm-dev/ -R -n dsm-dev

                oc process -f mplat-openshift/mplat-openshift-system/openshift/templates/mplat-dsm-deploy-internal-registry-ssl-template.yml \
                    APPLICATION_NAME=${config.microservice} TAG=${tag} -n dsm-dev | oc apply -f - -n dsm-dev
            """

            stage('Deploy in dsm-dev'){
                openshiftVerifyDeployment(
                    depCfg: config.microservice,
                    namespace: "dsm-dev",
                    replicaCount: '1',
                    apiURL: ocpUrl,
                    authToken: jenkinsToken
                )
            }

            pipelineUtils.apiTest(config.mavenCredentialsId, mavenArgs, config.microservice, "dsm-dev")

         

          

        } // node

    } catch (err) {
        currentBuild.result = 'FAILED'
        throw err
    }

}
