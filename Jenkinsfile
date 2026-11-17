// builds and tests the four services one at a time, never in parallel: their tests share the finconsult_test
// database. Then packages all four
pipeline {
    agent any

    environment {
        SPRING_DATASOURCE_URL = 'jdbc:postgresql://postgres:5432/finconsult_test'
        // the tests read the database from TEST_DB_URL (with DB_USERNAME and DB_PASSWORD from the container)
        TEST_DB_URL = 'jdbc:postgresql://postgres:5432/finconsult_test'
    }

    stages {
        stage('notification-service: build') {
            steps { dir('notification-service') { sh 'mvn clean compile -q' } }
        }
        stage('notification-service: test') {
            steps { dir('notification-service') { sh 'mvn test' } }
        }

        stage('user-service: build') {
            steps { dir('user-service') { sh 'mvn clean compile -q' } }
        }
        stage('user-service: test') {
            steps { dir('user-service') { sh 'mvn test' } }
        }

        stage('booking-service: build') {
            steps { dir('booking-service') { sh 'mvn clean compile -q' } }
        }
        stage('booking-service: test') {
            steps { dir('booking-service') { sh 'mvn test' } }
        }

        stage('document-service: build') {
            steps { dir('document-service') { sh 'mvn clean compile -q' } }
        }
        stage('document-service: test') {
            steps { dir('document-service') { sh 'mvn test' } }
        }

        stage('package') {
            steps {
                dir('notification-service') { sh 'mvn package -DskipTests -q' }
                dir('user-service') { sh 'mvn package -DskipTests -q' }
                dir('booking-service') { sh 'mvn package -DskipTests -q' }
                dir('document-service') { sh 'mvn package -DskipTests -q' }
            }
        }
    }

    post {
        success { echo 'All four services built, tested and packaged.' }
        failure { echo 'The pipeline failed, see the stage that is red.' }
    }
}
