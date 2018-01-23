#!/usr/bin/env groovy

def call (body) {
        def config = [:]
    	body.resolveStrategy = Closure.DELEGATE_FIRST
    	body.delegate = config
  	body()
     
        sh """
           oc project "${config.namespace}"

           oc import-image 'tarun-spog:${config.version}' --from=registry.netapp.com/nss/tarun-spog:${config.version} --confirm
    	"""
	  
 }


