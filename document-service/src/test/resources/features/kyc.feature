Feature: KYC Document Management
  Clients upload identity documents for verification. Admins review and approve or reject them.

  Scenario: Client uploads a KYC document
    Given I am logged in as a client
    When I upload an "INCOME_PROOF" document
    Then the response status is 201
    And the document status is "PENDING"

  Scenario: Unauthenticated user cannot upload
    Given I am not logged in
    When I try to upload an "INCOME_PROOF" document
    Then the response status is 401

  Scenario: Admin approves a KYC document
    Given I am logged in as a client
    And I have uploaded an "INCOME_PROOF" document
    When the admin approves my document
    Then the response status is 200
    And the document status is "APPROVED"

  Scenario: Admin rejects a KYC document
    Given I am logged in as a client
    And I have uploaded a "NATIONAL_ID" document
    When the admin rejects my document with reason "Document expired"
    Then the response status is 200
    And the document status is "REJECTED"
    And the rejection reason is "Document expired"

  Scenario: Client cannot upload duplicate document type
    Given I am logged in as a client
    And I already have a pending "INCOME_PROOF" document
    When I upload another "INCOME_PROOF" document
    Then the response status is 400
    And the error message is "You already have a pending INCOME_PROOF document"
