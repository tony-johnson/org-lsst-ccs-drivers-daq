pipeline {
    environment {
        CCS_NEXUS_CREDENTIALS = credentials('ccs-nexus-deployment')
    }
    agent {

        docker { 
            image 'ts-dockerhub.lsst.org/robotsal:alma9'
            args '-v /home/jenkins/ccs:/home/appuser/ccs'
            label "Node3_4CPU||Node1_4CPU||Node2_8CPU"
        }
//            label "Node3_4CPU||Node1_4CPU||Node2_8CPU"
    }
    tools {
        maven "maven 3.9.6"
        jdk "Java17"
    }

    parameters {
        booleanParam(name: "RELEASE",
                description: "Build a release from current commit.",
                defaultValue: false)
    }

    stages {
        stage ("Build and Deploy SNAPSHOT") {
            when {
                expression { ! params.RELEASE }
            }
            steps {
                sh "cat /home/appuser/ccs/maven/ccs-settings.xml"
                sh "/opt/maven/bin/mvn -s /home/appuser/ccs/maven/ccs-settings.xml -U clean install deploy:deploy site:site site:deploy" 
            }
        }

        stage("Release") {
            when {
                expression { params.RELEASE }
            }   
            steps {
                sh "/opt/maven/bin/mvn -s /home/appuser/ccs/maven/ccs-settings.xml -U -Dresume=false clean release:prepare release:perform"
            }
        }
    }

    post {
        always {
            sh "/home/appuser/ccs/scripts/updateJiraVersions.sh"

            //Email Notification
            step([$class: 'Mailer',
                notifyEveryUnstableBuild: true,
                recipients: emailextrecipients("mxturri@gmail.com",culprits(),requestor()),
                sendToIndividuals: true])
        }
    }

}
