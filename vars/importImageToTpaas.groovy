#!/usr/bin/env groovy

def call (String version) {
        def config = [:]
    	body.resolveStrategy = Closure.DELEGATE_FIRST
    	body.delegate = config
  	body()
       
	def pipelineUtils = new PipelineUtils()

        pipelineUtils.login(ocpUrl, jenkinsToken)
     
        sh """
           oc project "${config.namespace}"

           oc import-image 'tarun-spog:${version}' --from=registry.netapp.com/nss/tarun-spog:${version} --confirm
    	"""
	  
 }


