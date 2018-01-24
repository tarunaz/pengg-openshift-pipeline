#!/usr/bin/env groovy

def call (Map config) {
           
        sh """
           oc project "${config.deployNamespace}"

           oc import-image 'tarun-spog:${config.version}' --from=registry.netapp.com/nss/tarun-spog:${config.version} --confirm
    	"""
	  
 }


