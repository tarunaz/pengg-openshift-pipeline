#!/usr/bin/env groovy

import com.netapp.PipelineUtils

def call () {

	echo "Start OpenShift build"
      
	def pipelineUtils = new PipelineUtils()

	def buildConfigName = "${BASE}" + '-' + "${SOURCE_REPOSITORY_REF}"
	
	//Start build
        pipelineUtils.startOcpBuild("${BUILD_NAMESPACE}", buildConfigName)
 }


