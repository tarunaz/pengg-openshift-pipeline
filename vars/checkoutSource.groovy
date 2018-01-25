#!/usr/bin/env groovy

import com.netapp.PipelineUtils

def call (String gitRepoUrl) {

	 echo "Checkout source and openshift templates"

    	def branch = '*/' + "${SOURCE_REPOSITORY_REF}"

         // Checkout source code for version file
    	checkout([$class: 'GitSCM', branches: [[name: branch]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'app-root']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'a0abb0d8-4a01-4d4e-a4c7-90526325f245', url: "${SOURCE_REPOSITORY_URL}"]]])

	// Checkout openshift template files
    	checkout([$class: 'GitSCM', branches: [[name: branch]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'pengg-openshift']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'a0abb0d8-4a01-4d4e-a4c7-90526325f245', url: "${OCP_TEMPLATES_SRC_REPOSITORY_URL}"]]])
                     
	def pipelineUtils = new PipelineUtils()
                
	//Process templates
        pipelineUtils.processOcpTemplates("${OCP_BUILD_TEMPLATES_LOC}", "BASE=${BASE} SOURCE_REPOSITORY_URL=${SOURCE_REPOSITORY_URL} SOURCE_REPOSITORY_REF=${SOURCE_REPOSITORY_REF} GIT_PULL_SECRET=${GIT_PULL_SECRET} ANGULAR_HOME_DIR=web Version_File_Loc=version", "${BUILD_NAMESPACE}")

}
