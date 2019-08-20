# cgt-property-disposals

Service responsible for making DES requests to register a user for the CGT scheme and to submit applications.

**Get Business Partner Record**
----
  Returns the business partner record for a user.

* **URL**

  /business-partner-record

* **Method:**

  `POST`
  
*  **URL Params**

   **None**
 
* **Data Params**

  ```javascript
  {
    "nino" : "AB123456Z",
    "fname" : "Joe",
    "lname" : "Bloggs",
    "dateOfBirth" : "2000-10-11"  
  ```
  
  Date of birth must be in [LOCAL_ISO_DATE](https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html#ISO_LOCAL_DATE) format.

* **Success Response:**

  * **Code:** 200 <br />
    **Content:** 
    ```javascript
    { 
      dateOfBirth : 2000-10-11, 
      emailAddress : "joe.bloggs@gmail.com",
      address : { 
        line1 : "13 Some Lane", 
        line2 : "Some Town", 
        line3: "", 
        line4 : "", 
        postCode: "NN11 2RR"
      },
      sapNumber: "CG23423423"
    }
    ```
    
* **Error Response:**

  * **Code:** 400 BAD REQUEST <br />

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").