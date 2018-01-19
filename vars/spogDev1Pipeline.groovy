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
                checkout([$class: 'GitSCM', branches: [[name: '*/master']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'pengg-openshift']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'a0abb0d8-4a01-4d4e-a4c7-90526325f245', url: 'git@github.com:tarunaz/pengg-openshift.git']]])
                
                //pipelineUtils.processTemplateAndStartBuild("pengg-openshift/pengg-openshift-system/openshift/templates/pengg-runtime-bc-spog.yaml",
                //"BASE=${config.microservice} SOURCE_REPOSITORY_URL=${config.gitRepoUrl} SOURCE_REPOSITORY_REF=${config.sourceRepositoryRef} GIT_PULL_SECRET=${config.gitPullSecret} ANGULAR_HOME_DIR=web Version_File_Loc=version DEST_DEPLOY_NAMESPACE=${config.deployNamespace} authToken=${jenkinsToken} email_Address=${config.emailAddress}", config.buildNamespace, "${config.microservice}-${config.sourceRepositoryRef}")

    	    }


            echo "Openshift Tag image with custom version number..."
    	    stage('tag image') {
                echo "Check out source code..."
                checkout scm

                echo "readFile version "
                def VERSION = readFile 'version'
                echo "$VERSION"
	        openshiftTag alias: 'false', destStream: 'spog', destTag: "$VERSION", destinationNamespace: '', srcStream: 'spog', srcTag: 't1', verbose: 'false'
     	    }

	    echo "openshift import image at TPAAS..."
	    stage('import image to TPAAS') {
	        pipelineUtils.login(ocpUrl, jenkinsToken)
	     
                sh """
    	           oc project ${config.namespace}

                   oc process -f pengg-openshift/pengg-openshift-system/openshift/templates/pengg-spog-dc.yml \
                    NAME=${config.base} APPLICATION_IS_TAG_WEB="${config.microservice}:${config.sourceRepositoryRef}" APPLICATION_IS_TAG_API="${config.microservice}:${config.sourceRepositoryRef}" APPLICATION_IS_NM_WEB=${config.namespace} | oc apply -f - 

                   oc import-image 'spog' --from=registry.netapp.com/nss/spog --confirm --all
            	"""
	    }
            
	    echo "openshift deployement to TPAAS .."
            stage('deploy to TPAAS') {
           
		openshiftDeploy apiURL: $ocpUrl, depCfg: config.microservice, namespace: config.namespace,  verbose: 'true', waitTime: '', waitUnit: 'sec'
          		
 	    	echo "Verifying the deployment in TPASS..."
            	openshiftVerifyDeployment apiURL: $ocpUrl, depCfg: config.resourceName, namespace: config.namespace, replicaCount: '2', verbose: 'true', verifyReplicaCount: 'true', waitTime: '900', waitUnit: 'sec'
	    }
	} // node

    } catch (err) {
        currentBuild.result = 'FAILED'
        throw err
    }

}
