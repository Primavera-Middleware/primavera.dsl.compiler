package primavera.app.client;

import java.util.List;

server = "localhost:8080";

class ApiService {
    
    class User {
        type String name;
        type String address;
    }

	method User createUser(String name, String address) POST route = "/user/create";
	
	method User getUser(String id) GET route = "/user/{userId}";
    path param = (id=userId)
}
