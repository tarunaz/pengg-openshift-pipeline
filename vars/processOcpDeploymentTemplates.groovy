#!/usr/bin/env groovy

import com.netapp.PipelineUtils

def call (Map config) {
      
	def pipelineUtils = new PipelineUtils() 

	def jenkinsToken = readFile('/etc/jenkins/token')

	//login to TPAAS cluster
        pipelineUtils.login(config.ocpUrl, jenkinsToken)          
	
	pipelineUtils.processOcpTemplates("${OCP_DEPLOY_TEMPLATES_LOC}",
            "NAME=${BASE} APPLICATION_IS_TAG_WEB=${BASE}:1.7 APPLICATION_IS_TAG_API=${BASE}:${SOURCE_REPOSITORY_REF} APPLICATION_IS_NM_WEB=${DEPLOY_NAMESPACE}", "${DEPLOY_NAMESPACE}")

 }


