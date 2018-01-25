#!/usr/bin/env groovy

import com.netapp.PipelineUtils

def call (Map config) {
                     
	def pipelineUtils = new PipelineUtils()

	def jenkinsToken = readFile('/etc/jenkins/token')
                
	//Process templates
        pipelineUtils.processOcpTemplates("pengg-openshift/pengg-openshift-system/openshift/templates/pengg-runtime-bc-spog.yaml", "BASE=${BASE} SOURCE_REPOSITORY_URL=${SOURCE_REPOSITORY_URL} SOURCE_REPOSITORY_REF=${config.sourceRepositoryRef} GIT_PULL_SECRET=${config.gitPullSecret} ANGULAR_HOME_DIR=web Version_File_Loc=version DEST_DEPLOY_NAMESPACE=${config.deployNamespace} authToken=${jenkinsToken} email_Address=${config.emailAddress}", config.buildNamespace)


 }
