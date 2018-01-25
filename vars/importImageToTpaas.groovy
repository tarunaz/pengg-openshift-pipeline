#!/usr/bin/env groovy

def call () {
           
	echo "Import OpenShift Image to TPAAS"
	
        sh """
           oc project "${DEPLOY_NAMESPACE}"

           oc import-image "${BASE}":"${VERSION}" --from=registry.netapp.com/nss/"${BASE}":"${VERSION}" --confirm
    	"""
	  
 }


