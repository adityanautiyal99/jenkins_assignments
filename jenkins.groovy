@Library('jenkis') _
job

pipeline {
    agent any
    stages {
       stage('cloning the directory') {
            steps {
                git 'https://github.com/opstree/spring3hibernate.git'
            }
        }
        stage('cleaning the target directory') {
            steps {
                sh '''
                mvn clean compile
                '''
            }
        }
        stage('Testing the code') {
            steps {
                script{
                    try{
                     sh "mvn test"
                    } catch (e) {
                             currentBuild.result = "FAILED"
                             throw e
                           }
                }
            }
        }
        stage('sending analysis and creating package'){
              parallel {
                stage('sending test analysis to SonarQube') {
                    steps {
                        withSonarQubeEnv('sonar') {
                        sh "mvn sonar:sonar"
                     }
                 }
             }
        stage('Creating package') {
            steps {
                script {
                    try {
                        sh "mvn package"
                    } catch (e) {
                             currentBuild.result = "FAILED"
                             throw e
                          }
                     }
                 }
              }
           }
        }
        stage('ArchiveArtifact'){
            steps {
                    sh "mvn surefire-report:report"
                    archiveArtifacts 'target/*.war'   
            }
        }
       /*stage("Uploading Artifact"){
            steps{
                rtServer (
                id: 'key',
                url: 'http://52f4e1510188.ngrok.io/artifactory',
                // If you're using username and password:
                username: 'admin',
                password: '6DE@Dpool9',
                // If you're using Credentials ID:
                //credentialsId: 'ccrreeddeennttiiaall',
                // Configure the connection timeout (in seconds).
                // The default value (if not configured) is 300 seconds:
                timeout: 300
                )
                
                rtUpload (
                serverId: 'key',
                spec: '''{
                      "files": [
                        {
                          "pattern": "target/*.war",
                          "target": "key/Build_${BUILD_NUMBER}/"
                        }
                     ]
                }'''
                )
                /*rtDownload (
                serverId: 'key',
                spec: '''{
                        "files": [
                        {
                            "pattern": "key/Build_${BUILD_NUMBER}/",
                            "target": "aditya/Build_${BUILD_NUMBER}"
                        }
                    ]
                }''' 
                )
            }
        }*/
    }
    post {
        success {
            notifySuccessful()
        }
        failure{
            notifyFailed()
        }
        always{
            cobertura autoUpdateHealth: false, autoUpdateStability: false, coberturaReportFile: ' **/target/site/cobertura/coverage.xml', conditionalCoverageTargets: '70, 0, 0', failUnhealthy: false, failUnstable: false, lineCoverageTargets: '80, 0, 0', maxNumberOfBuilds: 0, methodCoverageTargets: '80, 0, 0', onlyStable: false, sourceEncoding: 'ASCII', zoomCoverageChart: false
            publishCoverage adapters: [coberturaAdapter('**/target/site/cobertura/coverage.xml')], sourceFileResolver: sourceFiles('NEVER_STORE')
            publishHTML(target : [allowMissing: false, alwaysLinkToLastBuild: true, keepAll: false, reportDir: '/var/lib/jenkins/workspace/pi/target/site', reportFiles: 'surefire-report.html', reportName: 'HTML Report', reportTitles: ''])
        }
    }
}
def notifyFailed() {
    slackSend (color: '#00FF00', message: "Failed: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]' (${env.BUILD_URL})")
}
def notifySuccessful() {
    emailext attachLog: true,
    attachmentsPattern: 'target/site/surefire-report.html',
                body: "${currentBuild.currentResult}: Job ${env.JOB_NAME} build ${env.BUILD_NUMBER}\n More info at: ${env.BUILD_URL}",
                recipientProviders: [developers(), requestor()],
                subject: "Jenkins Build ${currentBuild.currentResult}: Job ${env.JOB_NAME}"
    slackSend (color: '#00FF00', message: "SUCCESSFUL: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]' (${env.BUILD_URL})")
    build job: 'upload',
        parameters: [
            string(name: 'BUILD_NU', value: 'Build_' + String.valueOf(env.BUILD_NUMBER))
        ]
}
