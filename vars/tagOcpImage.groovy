#!/usr/bin/env groovy

def call () {
       
	echo "Tag OpenShift Image"
	
	echo "readFile version "	
        def version = readFile 'app-root/version'

	// Set env
	env.VERSION = "$version"
        
	echo "${VERSION}"
        openshiftTag alias: 'false', destStream: "${BASE}", destTag: "${VERSION}", destinationNamespace: '', srcStream: "${BASE}", srcTag: 'master', verbose: 'false'
 }


