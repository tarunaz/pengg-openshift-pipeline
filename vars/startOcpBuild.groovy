#!/usr/bin/env groovy

import com.netapp.PipelineUtils

def call (body) {
        def config = [:]
    	body.resolveStrategy = Closure.DELEGATE_FIRST
    	body.delegate = config
        body()
                          
	def pipelineUtils = new PipelineUtils()
	
	def buildConfigName = Constants.MICROSERVICE + '-' + Constants.GIT_SRC_REPOSITORY_REF

	//Start build
        pipelineUtils.startOcpBuild(config.namespace, config.buildConfigName)
 }


