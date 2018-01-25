#!/usr/bin/env groovy

import com.netapp.PipelineUtils

def call () {
                            
	def pipelineUtils = new PipelineUtils()

	def buildConfigName = config.microservice + '-' + config.sourceRepositoryRef
	
	//Start build
        pipelineUtils.startOcpBuild(config.buildNamespace, buildConfigName)
 }


