#!/usr/bin/env groovy

import com.netapp.PipelineUtils

def call (Map config) {
                     
	def pipelineUtils = new PipelineUtils()

	def jenkinsToken = readFile('/etc/jenkins/token')
                
	//Process templates
        pipelineUtils.processOcpTemplates("${OCP_BUILD_TEMPLATES_LOC}", "BASE=${BASE} SOURCE_REPOSITORY_URL=${SOURCE_REPOSITORY_URL} SOURCE_REPOSITORY_REF=${SOURCE_REPOSITORY_REF} GIT_PULL_SECRET=${GIT_PULL_SECRET} ANGULAR_HOME_DIR=web Version_File_Loc=version", "${BUILD_NAMESPACE}")


 }
