Preferred Package Structure


* config 
* resource
  * http "entrypoints", extend resteasy Resource interfaces that are generated with openapi generator 
  * resource should have an injected service and not inject a repository class directly
* model
  * Dtos and other pojos 
* MapStruct mappers 
* exception 
  * extending Exception
* service 
  * Business logic, orchestration, things that aren't appropriate in the other packages 
  * entities should be converted to DTO in this layer prior to being returned to resource classes 
  * kafka producer and consumers live here 
  * umb communication lives here 
  * interact with the database by having a service class inject a repository class 
* repository 
  * PanacheRepository and JpaRepository 
  * JPA entities 
    * Class names should have "Entity" suffix to differentiate them from their DTO counterparts