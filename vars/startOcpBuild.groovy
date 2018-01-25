#!/usr/bin/env groovy

import com.netapp.PipelineUtils

def call () {
                            
	def pipelineUtils = new PipelineUtils()

	def buildConfigName = "${BASE}" + '-' + "${SOURCE_REPOSITORY_REF}"
	
	//Start build
        pipelineUtils.startOcpBuild("${BUILD_NAMESPACE}, buildConfigName)
 }


