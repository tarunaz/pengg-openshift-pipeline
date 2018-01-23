#!/usr/bin/env groovy

def call (String gitRepoUrl, String sourceRepositoryRef, String gitPullSecret, String deployNamespace,
	String emailAddress, String buildNamespace, String microservice) {
                
 	def jenkinsToken = readFile('/etc/jenkins/token')
                
	//Process templates and start build
        pipelineUtils.processTemplateAndStartBuild("pengg-openshift/pengg-openshift-system/openshift/templates/pengg-runtime-bc-spog.yaml", "BASE=${config.microservice} SOURCE_REPOSITORY_URL=${gitRepoUrl} SOURCE_REPOSITORY_REF=${sourceRepositoryRef} GIT_PULL_SECRET=${gitPullSecret} ANGULAR_HOME_DIR=web Version_File_Loc=version DEST_DEPLOY_NAMESPACE=${deployNamespace} authToken=${jenkinsToken} email_Address=${emailAddress}", ${buildNamespace}, "${microservice}-${sourceRepositoryRef}")

 }


