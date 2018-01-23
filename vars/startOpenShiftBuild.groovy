#!/usr/bin/env groovy

import com.netapp.PipelineUtils

def call (body) {
        def config = [:]
    	body.resolveStrategy = Closure.DELEGATE_FIRST
    	body.delegate = config
        body()
                          
	//Start build
        pipelineUtils.startBuild(config.namespace, config.buildConfigName)
 }


