#!/usr/bin/env groovy

def call (Map config) {
       
	def branch = '*/' + "${SOURCE_REPOSITORY_REF}"

         // Checkout source code for version file
    	checkout([$class: 'GitSCM', branches: [[name: branch]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'app-root']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'a0abb0d8-4a01-4d4e-a4c7-90526325f245', url: "${SOURCE_REPOSITORY_URL}"]]])

	echo "readFile version "	
        def VERSION = readFile 'app-root/version'

	// Set env
	env.VERSION = "$VERSION"
        
	echo "${VERSION}"
        openshiftTag alias: 'false', destStream: "${BASE}", destTag: "${VERSION}", destinationNamespace: '', srcStream: "${BASE}", srcTag: 'master', verbose: 'false'
 }


