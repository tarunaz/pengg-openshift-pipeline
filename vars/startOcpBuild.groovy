#!/usr/bin/env groovy

import com.netapp.PipelineUtils

def call (Map config) {
                            
	def pipelineUtils = new PipelineUtils()

	def buildConfigName = config.microservice + '-' config.sourceRepositoryRef
	
	//Start build
        pipelineUtils.startOcpBuild(config.buildNamespace, buildConfigName)
 }


