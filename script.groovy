pipeline {
    agent any
    tools {
        maven 'maven_3'
        jdk 'jdk17'
    }
    environment {
        GOOGLE_APPLICATION_CREDENTIALS = credentials('gcp-terraform-key')
    }
    stages {
        stage('CI: Build App') {
            steps {
                dir('app-code') {
                    git branch: 'main', url:'https://github.com/vrdeepu/helloworld-gcp.git'
                    bat 'mvn clean package'
                }
            }
        }

        stage('CD: Infra Apply') {
            steps {
                dir('infra-code') {
                    git branch: 'main', url:'https://github.com/vrdeepu/helloworld-infra.git'
                    echo "Initializing and Provisioning..."
                    bat 'terraform init'
                    bat 'terraform apply -auto-approve'
                }
            }
        }

        stage('CD: Deploy JAR to VM') {
            steps {
                dir('app-code') {
                    script {
                        // 1. Set the project
                        bat "call gcloud config set project project-cb063053-ca79-4fea-9b1"

                        // 2. Fetch the Names (Corrected variable name here)
                        def vmOutput = bat(
                                script: "@call gcloud compute instances list --filter=\"name~'basic-hello-vm'\" --format=\"value(name)\"",
                                returnStdout: true
                        ).trim()

                        // Split the string into a list
                        def vmList = vmOutput.split('\r?\n')

                        echo "Found ${vmList.size()} VMs to deploy to: ${vmList}"

                        // 3. Wait for initialization
                        echo "Waiting 5 minutes for all VMs to initialize Java..."
                        bat "ping 127.0.0.1 -n 301 > nul"

                        // 4. Loop through each VM name
                        for (vmName in vmList) {
                            if (vmName.trim()) {
                                echo "--- Deploying to VM: ${vmName} ---"

                                // Copy the JAR using VM name
                                bat "call gcloud compute scp target\\helloworld-gcp-1.0-SNAPSHOT.jar ${vmName}:/tmp/app.jar --zone=us-central1-a --quiet"

                                // SSH and start using VM name
                                bat """
                            call gcloud compute ssh ${vmName} --zone=us-central1-a --quiet --command="sudo fuser -k 8081/tcp || true && nohup java -jar /tmp/app.jar > /tmp/app.log 2>&1 &"
                        """

                                echo "Deployment to ${vmName} complete."
                            }
                        } // End of For Loop
                    } // End of Script
                } // End of Dir
            } // End of Steps
        } // End of Stage

        stage('Cleanup: Manual Approval') {
            steps {
                script {
                    input message: "App is deployed! Check the IP, then click proceed to DESTROY.", ok: "Destroy Now"
                }
                dir('infra-code') {
                    echo "Cleaning up Infrastructure..."
                    bat 'terraform destroy -auto-approve'
                }
            }
        }
    }
}