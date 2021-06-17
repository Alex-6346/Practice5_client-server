package practice5;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.*;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import practice1.Product;
import practice4.SQLTest;
import practice4.User;

import javax.crypto.spec.SecretKeySpec;

public class MyServer {

    private static final byte[] API_KEY_SECRET_BYTES = "my-secret-key-aqwertyuioplkhjgdsazxcbnmkjhg".getBytes(StandardCharsets.UTF_8);
    private static final String PATH = "/api/good/";

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8765), 0);
        server.start();
        ObjectMapper objectMapper = new ObjectMapper();

        SQLTest sqlTest = new SQLTest();
        sqlTest.initialization();
        sqlTest.insertUser(new User("login1", "password1"));
        sqlTest.insertUser(new User("login2", "password2"));
        sqlTest.insertUser(new User("login3", "password3"));
        sqlTest.insertProduct(new Product("kapusta",20.3,20));
        sqlTest.insertProduct(new Product("kartoshka",10.32,5.2));
        sqlTest.insertProduct(new Product("kabachOk",5.6,1));

        //AUTHORIZATION:
        Authenticator authenticator = new Authenticator() {
            @Override
            public Result authenticate(HttpExchange exch) {
                String jwt= exch.getRequestHeaders().getFirst("Authorization");
                if(jwt!=null) {
                    String login = getUserLoginFromJWT(jwt);
                    User user = sqlTest.getUserByLogin(login);
                    if(user!=null){
                        return new Success(new HttpPrincipal(login,"admin"));
                    }
                }
                return new Failure(403);
            }
        };


        server.createContext("/", exchange -> {
            byte[] response = "{\"status\": \"ok\" }".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });


        //AUTHORIZATION CONTEXT:
        server.createContext("/login", exchange -> {
            if (exchange.getRequestMethod().equals("POST")) {
                User user = objectMapper.readValue(exchange.getRequestBody(), User.class);
                User fromDb= sqlTest.getUserByLogin(user.getLogin());
                if(fromDb!=null)
                {
                    if(fromDb.getPassword().equals(user.getPassword()))
                    {
                        String jwt=createJWT(fromDb.getLogin());
                        System.out.println(getUserLoginFromJWT(jwt));
                        exchange.getResponseHeaders().set("Authorization", jwt);
                        exchange.sendResponseHeaders(200, 0);
                    }
                    else { exchange.sendResponseHeaders(401, 0); }
                }
                else { exchange.sendResponseHeaders(401, 0); }

            } else {
                exchange.sendResponseHeaders(405, 0);
            }
            exchange.close();
        });


        //ID OF PRODUCT CONTEXT:
        server.createContext("/api/good/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String requestString = path.substring(PATH.length());
            int requestId =0;

            try{
                requestId=Integer.parseInt(requestString);
            } catch (NumberFormatException e) {
                exchange.sendResponseHeaders(404, 0); exchange.close();
            }

                Product selectedProduct = sqlTest.getProductById(requestId);

            //-----------------GET:----------------\\
            if (exchange.getRequestMethod().equals("GET")) {
                if (selectedProduct != null) {
                    byte[] response = objectMapper.writeValueAsBytes(selectedProduct);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, response.length);
                    exchange.getResponseBody().write(response);
                }
                else{ exchange.sendResponseHeaders(404, 0); }
            }


            //-----------------POST:----------------\\
            else if (exchange.getRequestMethod().equals("POST")){
                Product updatedProduct=new Product();
                try {
                    updatedProduct = objectMapper.readValue(exchange.getRequestBody(), Product.class);
                }
                catch (JsonMappingException exception){
                    exchange.sendResponseHeaders(404,0);
                }
                if (updatedProduct != null && selectedProduct!=null) {
                    if(updatedProduct.getPrice()>0 &&updatedProduct.getAmount()>0) {
                        sqlTest.updateProductByValuesAndId(selectedProduct, updatedProduct);
                        byte[] response = "{\"status\": \"No Content\" }".getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().set("Content-Type", "application/json");
                        exchange.sendResponseHeaders(204, response.length);
                        exchange.getResponseBody().write(response);
                    }
                    else{exchange.sendResponseHeaders(409, 0); }
                }
                else{ exchange.sendResponseHeaders(404, 0); }
            }

            //-----------------DELETE:----------------\\
            else if (exchange.getRequestMethod().equals("DELETE")){
                if(selectedProduct!=null){
                    sqlTest.deleteProductByValuesAndId(selectedProduct);
                    byte[] response = "{\"status\": \"No Content\" }".getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(204, response.length);
                    exchange.getResponseBody().write(response);
                }
                else{exchange.sendResponseHeaders(404, 0);}
            }

            //-----------------OTHERS:----------------\\
            else{exchange.sendResponseHeaders(404, 0);}

            exchange.close();
        })
                .setAuthenticator(authenticator);



        // "/api/good" CONTEXT:
        server.createContext("/api/good", exchange -> {

            if (exchange.getRequestMethod().equals("PUT")) {
                Product createdProduct=new Product();
                try {
                    createdProduct = objectMapper.readValue(exchange.getRequestBody(), Product.class);
                }
                catch (JsonMappingException exception){
                    exchange.sendResponseHeaders(404,0);
                }
                if(createdProduct.getPrice()>0&&createdProduct.getAmount()>0){
                    int createdId = sqlTest.insertProduct(createdProduct).getId();
                    byte[] response = ("{ \"status\": \"Created\",  \"product-id\": \""+ createdId + "\" }").getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(201, response.length);
                    exchange.getResponseBody().write(response);
                }
                else{exchange.sendResponseHeaders(409, 0);}

            }
            else{exchange.sendResponseHeaders(404, 0);}

            exchange.close();
        })
                .setAuthenticator(authenticator);




    }





    private static String createJWT(String login) {
        SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.HS256;
        Date now = new Date();
        Key signingKey = new SecretKeySpec(API_KEY_SECRET_BYTES, signatureAlgorithm.getJcaName());

        //Let's set the JWT Claims
      return  Jwts.builder()
                .setIssuedAt(now)
                .setSubject(login)
                .signWith(signingKey,signatureAlgorithm).compact();
    }

    private static String getUserLoginFromJWT(String jwt) {
        SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.HS256;
        Date now = new Date();
        Key signingKey = new SecretKeySpec(API_KEY_SECRET_BYTES, signatureAlgorithm.getJcaName());



        //This line will throw an exception if it is not a signed JWS (as expected)
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(jwt).getBody();
        //System.out.println("ID: " + claims.getId());
        //System.out.println("Subject: " + claims.getSubject());
        //System.out.println("Issuer: " + claims.getIssuer());
        //System.out.println("Expiration: " + claims.getExpiration());

        return claims.getSubject();
    }

}