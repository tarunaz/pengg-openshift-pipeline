#!/usr/bin/env groovy

import com.netapp.PipelineUtils

def call (Map config) {
      
	def pipelineUtils = new PipelineUtils() 

	def jenkinsToken = readFile('/etc/jenkins/token')

	//login to TPAAS cluster
        pipelineUtils.login(config.ocpUrl, jenkinsToken)          
	
	pipelineUtils.processOcpTemplates("pengg-openshift/pengg-openshift-system/openshift/templates/pengg-spog-dc.yml",
            "NAME=${config.microservice} APPLICATION_IS_TAG_WEB=${config.microservice}:1.7 APPLICATION_IS_TAG_API=${config.microservice}:${config.sourceRepositoryRef} APPLICATION_IS_NM_WEB=${config.namespace}", config.deployNamespace)

 }


