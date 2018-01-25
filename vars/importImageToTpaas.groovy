#!/usr/bin/env groovy

def call () {
           
        sh """
           oc project "${DEPLOY_NAMESPACE}"

           oc import-image "${BASE}":"${VERSION}" --from=registry.netapp.com/nss/"${BASE}":"${VERSION}" --confirm
    	"""
	  
 }


