package exa.vertx.office;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.StaticHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;

import java.util.*;

public class HttpServerVerticle extends AbstractVerticle {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpServerVerticle.class);

    public static final String CONFIG_HTTP_SERVER_PORT = "http.server.port";
    public static final String CONFIG_APIDB_QUEUE = "epimdb.queue";
    public static final String PUBLIC_IMAGES = "public.images";
    public static final String PROFILE_IMAGES = "public.profile";
    public static final String FILE_LOCATION="file.location";
    public static final String PUBLIC_ASSETS_DOKUMEN="public.assets.sharedoc";
    public static final String PUBLIC_ASSETS_LAPORAN="public.assets.laporan";
    public static final String PUBLIC_ASSETS_TEMPLATE="public.assets.template";
    private static final String ALPHA_NUMERIC_STRING = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int DEFAULT_PORT = 8085;


    private String epimDbQueue = "epimdb.queue";
    private String urlPath;
    private String urlPathDoc;
    private String urlPathTemplate;
    private String urlPathLaporan;
    private String fileStore;
    private JWTAuth jwtAuth;

    @Override
    public void start(Future<Void> startFuture) throws Exception {

        epimDbQueue = config().getString(CONFIG_APIDB_QUEUE, "epimdb.queue");

        urlPath= config().getString(PUBLIC_IMAGES,"public.images");

        urlPathDoc = config().getString(PUBLIC_ASSETS_DOKUMEN, "public.assets.document");

        urlPathLaporan = config().getString(PUBLIC_ASSETS_LAPORAN, "public.assets.laporan");
        
        urlPathTemplate = config().getString(PUBLIC_ASSETS_TEMPLATE, "public.assets.template");

        String avatarPath = config().getString(PROFILE_IMAGES,"public.profiles");

        fileStore= config().getString(FILE_LOCATION,"file.location");

        // Generate the certificate for https
       // SelfSignedCertificate cert = SelfSignedCertificate.create();

        // tag::https-server[]
        //HttpServer server = vertx.createHttpServer((new HttpServerOptions()
          //      .setSsl(true)
            //    .setKeyCertOptions(cert.keyCertOptions())));

        HttpServer server = vertx.createHttpServer();


        // end::https-server[]

        Router router = Router.router(vertx);


        Set<String> allowHeaders = new HashSet<>();
        allowHeaders.add("x-requested-with");
        allowHeaders.add("Access-Control-Allow-Origin");
        allowHeaders.add("Access-Control-Allow-Headers");
        allowHeaders.add("origin");
        allowHeaders.add("Content-Type");
        allowHeaders.add("accept");
        allowHeaders.add("Authorization");
        allowHeaders.add("X-Requested-With");
        allowHeaders.add("X-Auth-Token");
        Set<HttpMethod> allowMethods = new HashSet<>();
        allowMethods.add(HttpMethod.GET);
        allowMethods.add(HttpMethod.POST);
        allowMethods.add(HttpMethod.DELETE);
        allowMethods.add(HttpMethod.PATCH);
        allowMethods.add(HttpMethod.OPTIONS);

        router.route().handler(CorsHandler.create("*")
                .allowedHeaders(allowHeaders)
                .allowedMethods(allowMethods));
        router.route().handler(BodyHandler.create());

        //[start sub-route]
        Router apiRouter = Router.router(vertx);
        Router apiAsset = Router.router(vertx);



        jwtAuth = JWTAuth.create(vertx, new JsonObject()
                .put("keyStore", new JsonObject()
                        .put("path", "keystore.jceks")
                        .put("type", "jceks")
                        .put("password", "secret")));

      //  apiRouter.route().handler(JWTAuthHandler.create(jwtAuth, "/eoffice/api/login"));

        apiRouter.get("/token").handler(ctx -> {
            ctx.response().putHeader("Content-Type", "application/json");
            ctx.response().end(jwtAuth.generateToken(new JsonObject(), new JWTOptions().setExpiresInMinutes((int) 12L)));
        });


        apiRouter.post("/login").handler(this::loginHandler);


        // [start new method, cause patching db]
        apiRouter.get("/getProfile/:username/:password").handler(this::getProfile);
        apiRouter.get("/getMail/:username/:mailType").handler(this::getMail);
        apiRouter.get("/getMailTo/:mailId").handler(this::getMailTo);
        apiRouter.get("/getEmpTree/:pegaId").handler(this::getEmpTree);
        apiRouter.get("/getGraphMail/:pegaNip").handler(this::getGraphMail);
        apiRouter.get("/getAttachment/:mailId").handler(this::getAttachment);
        apiRouter.get("/getReportByMail/:mailId").handler(this::getReportByMail);
        apiRouter.get("/getReportByNip/:mailId/:manoId").handler(this::getReportByNip);
        apiRouter.get("/getDraftChat/:mailId").handler(this::getDraftChat);
        apiRouter.get("/getShareDoc/:share").handler(this::getShareDoc);

        apiRouter.post("/push").handler(this::pushNotification);
        // Enable multipart form data parsing
        apiRouter.post("/upload").handler(BodyHandler.create()
                .setUploadsDirectory(FILE_LOCATION));

        apiRouter.post("/upload").handler(this::postReport);
        // [end tag method get]


        // [start method post]
        apiRouter.post("/disposisiTo").handler(this::disposisiTo);
        apiRouter.post("/deleteStaff").handler(this::disposeStaff);
        apiRouter.post("/postDraft").handler(this::postMReqPlay);

        // [end tag : method post]

        //  apiRouter.route("/static/*").handler(StaticHandler.create());
        // serve static resources
        apiAsset.route("/assets/*").handler(StaticHandler.create().setCachingEnabled(false).setWebRoot("assets"));


        router.mountSubRouter("/eoffice/api", apiRouter);
        router.mountSubRouter("/eoffice/static",apiAsset);



        int portNumber = config().getInteger(CONFIG_HTTP_SERVER_PORT, DEFAULT_PORT);  // <3>
        server
                .requestHandler(router::accept)
                .listen(portNumber, ar -> {
                    if (ar.succeeded()) {
                        LOGGER.info("HTTP server e-office running on port " + portNumber);
                        startFuture.complete();
                    } else {
                        LOGGER.error("Could not start a HTTP server", ar.cause());
                        startFuture.fail(ar.cause());
                    }
                });
    }

    private void getProfile(RoutingContext context) {
        LOGGER.info("getProfile");

        String userName = context.request().getParam("username");
        String password = context.request().getParam("password");


        JsonObject request = new JsonObject().put("userName", userName).put("password",password);


        DeliveryOptions options = new DeliveryOptions().addHeader("action", "get-profile");

        vertx.eventBus().send(epimDbQueue, request, options, reply -> {
            if (reply.succeeded()) {
                JsonObject body = (JsonObject) reply.result().body();

                LOGGER.info("urlPathDoc: " + urlPathDoc);

                context.response().putHeader("Content-Type", "application/json");
                context.response().end(body.encodePrettily().replaceAll("\\\\","")
                        .replaceAll("\\\\","")
                        .replaceFirst("\"\\[","[")
                        .replaceFirst("\"data\" : \\[","\"data\":")
                        .replaceFirst("],",",")
                        .replaceFirst("\"\\]\"","]")
                        .replaceAll("}]}]\"","}]}]")
                        .replaceAll("\\} \\]", ""));

            } else {
                context.fail(reply.cause());
            }
        });
    }

    private void postMReqPlay(RoutingContext context) {
        LOGGER.info("postMReqPlay");
        JsonObject request = context.getBodyAsJson();
        JsonObject jsonData = request.getJsonObject("data");

        // LOGGER.info("jsondata: "+jsonData);
        String imgBase64 = jsonData.getString("replay");
        String unor = jsonData.getString("org");
        String pegaNip = jsonData.getString("username");

        LOGGER.info("json: " + unor + "/" + pegaNip);

        Date date = new Date();
        LocalDate localDate = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        int year = localDate.getYear();
        int month = localDate.getMonthValue();
        int day = localDate.getDayOfMonth();

        String random = randomAlphaNumeric(8);

        LOGGER.info("random: " + random);

        String fileName = unor + pegaNip + year + month + day + "_" + random + ".png";

        String filePath = fileStore + pegaNip + "/" + fileName;

        LOGGER.info(filePath);

        File file = new File(filePath);

        // add filename attribute to json
        request.put("filename", fileName);

        //LOGGER.info("newRequest before: "+request);

        // use blocking handler for handle blocking process
        vertx.<String>executeBlocking(future -> {

            File fileDirectory = new File(fileStore + pegaNip);

            if (!fileDirectory.exists()) {
                fileDirectory.mkdir();
                LOGGER.info("directory created");
            }

            // decode base64 encoded image

            try (FileOutputStream imageOutFile = new FileOutputStream(file)) {

                if (!file.exists()) {
                    file.createNewFile();
                }
                // Converting a Base64 String into Image byte array
                byte[] imageByteArray = Base64.getDecoder().decode(imgBase64);

                imageOutFile.write(imageByteArray);
            } catch (FileNotFoundException e) {
                LOGGER.info("Image not found " + e);
            } catch (IOException ioe) {
                LOGGER.info("Exception while reading the Image " + ioe);
            }

            String result = "succeed";

            future.complete(result);

        }, res -> {

            if (res.succeeded()) {
                //  LOGGER.info("file created");

                //    LOGGER.info("newRequest:"+request);

                DeliveryOptions options = new DeliveryOptions().addHeader("action", "post-mail-mreqplay");

                vertx.eventBus().send(epimDbQueue, request, options, reply -> {
                    if (reply.succeeded()) {
                        JsonObject body = (JsonObject) reply.result().body();
                        context.response().putHeader("Content-Type", "application/json");
                        context.response().end(body.encode());

                    } else {
                        context.fail(reply.cause());
                    }
                });

            } else {
                res.failed();
                //context.fail(reply.cause());
            }
        });
    }

    private void getShareDoc(RoutingContext context) {
        LOGGER.info("getShareDoc");

        String share = context.request().getParam("share");

        JsonObject request = new JsonObject().put("share", share);

        // LOGGER.info("mailId: "+mailId);

        DeliveryOptions options = new DeliveryOptions().addHeader("action", "get-share-doc");

        vertx.eventBus().send(epimDbQueue, request, options, reply -> {
            if (reply.succeeded()) {
                JsonObject body = (JsonObject) reply.result().body();

                LOGGER.info("urlPathDoc: " + urlPathDoc);

                context.response().putHeader("Content-Type", "application/json");
                context.response()
                        .end(body.encodePrettily().replaceAll("\\\\", "").replaceAll("\"doc\" : \\[ \\{", "")
                                .replaceAll("\"data\" : \"", "\"data\" : ")
                                .replaceAll("\"uri\":", "\"uri\" :\"" + urlPathDoc).replaceAll("dokumen/\"", "dokumen/")
                                .replaceAll("}]}]\"", "}]}]").replaceAll("\\} \\]", "").replaceAll("]\"", "]"));

            } else {
                context.fail(reply.cause());
            }
        });
    }
    
    private void getDraftChat(RoutingContext context) {
        LOGGER.info("getDraftChat");

        Integer mailId = Integer.parseInt(context.request().getParam("mailId"));

        JsonObject request = new JsonObject().put("mailId", mailId);

        // LOGGER.info("mailId: "+mailId);

        DeliveryOptions options = new DeliveryOptions().addHeader("action", "get-draft-chat");

        vertx.eventBus().send(epimDbQueue,request, options, reply -> {
            if (reply.succeeded()) {
                JsonObject body = (JsonObject) reply.result().body();

                LOGGER.info("urlpath: "+urlPath);


                context.response().putHeader("Content-Type", "application/json");
                context.response().end(body.encodePrettily()
                        .replaceAll("\\\\","")
                        .replaceAll("\"uri\" :","\"uri\" : \""+urlPath)
                        .replaceAll("images/ \"","images/"));


            } else {
                context.fail(reply.cause());
            }
        });
    }

    private void getReportByNip(RoutingContext context) {
        LOGGER.info("getReportByNip");

        Integer mailId = Integer.parseInt(context.request().getParam("mailId"));
        Integer manoId = Integer.parseInt(context.request().getParam("manoId"));
        JsonObject request = new JsonObject().put("mailId", mailId).put("manoId",manoId);

       // LOGGER.info("mailId: "+mailId);

        DeliveryOptions options = new DeliveryOptions().addHeader("action", "get-report-by-nip");

        vertx.eventBus().send(epimDbQueue,request, options, reply -> {
            if (reply.succeeded()) {
                JsonObject body = (JsonObject) reply.result().body();

                LOGGER.info("urlpath: "+urlPath);


                context.response().putHeader("Content-Type", "application/json");
                context.response().end(body.encodePrettily()
                        .replaceAll("\\\\","")
                        .replaceAll("\"uri\" :","\"uri\" : \""+urlPathLaporan)
                        .replaceAll("laporan/ \"","laporan/"));


            } else {
                context.fail(reply.cause());
            }
        });
    }

    private void getReportByMail(RoutingContext context) {
        LOGGER.info("getReportByMail");

        Integer mailId = Integer.parseInt(context.request().getParam("mailId"));
        JsonObject request = new JsonObject().put("mailId", mailId);

        LOGGER.info("mailId: "+mailId);

        DeliveryOptions options = new DeliveryOptions().addHeader("action", "get-report-by-mail");

        vertx.eventBus().send(epimDbQueue,request, options, reply -> {
            if (reply.succeeded()) {
                JsonObject body = (JsonObject) reply.result().body();

                LOGGER.info("urlpath: "+ urlPathLaporan);


                context.response().putHeader("Content-Type", "application/json");
                context.response().end(body.encodePrettily()
                        .replaceAll("\\\\","")
                        .replaceAll("\"report\" : \\[ \\{","")
                        .replaceAll("\"data\" : \"","\"data\" : ")
                        .replaceAll("\"uri\":","\"uri\" :\""+urlPathLaporan)
                        .replaceAll("images/\"", "images/")
                        .replaceAll("laporan/\"", "laporan/")
                        .replaceAll("}]}]\"","}]}]")
                        .replaceAll("\\} \\]","")
                        .replaceAll("]\"","]"));


            } else {
                context.fail(reply.cause());
            }
        });
    }

    private void getAttachment(RoutingContext context) {
        LOGGER.info("getAttachment");

        Integer mailId = Integer.parseInt(context.request().getParam("mailId"));
        JsonObject request = new JsonObject().put("mailId", mailId);

        LOGGER.info("mailId: "+mailId);

        DeliveryOptions options = new DeliveryOptions().addHeader("action", "get-attachment");

        vertx.eventBus().send(epimDbQueue,request, options, reply -> {
            if (reply.succeeded()) {
                JsonObject body = (JsonObject) reply.result().body();

                LOGGER.info("urlpath: "+urlPathTemplate);


                context.response().putHeader("Content-Type", "application/json");
                context.response().end(body.encodePrettily()
                        .replaceAll("\\\\","")
                        .replaceAll("\"attachment\" : \\[ \\{","")
                        .replaceAll("\"data\" : \"","\"data\" : ")
                        .replaceAll("\"uri\":","\"uri\" :\""+ urlPathTemplate)
                        .replaceAll("images/\"", "images/")
                        .replaceAll("template/\"", "template/")
                        .replaceAll("}]}]\"","}]}]")
                        .replaceAll("\\} \\]","")
                        .replaceAll("]\"","]"));


            } else {
                context.fail(reply.cause());
            }
        });
    }

    private void pushNotification(RoutingContext context) {
        JsonObject request = context.getBodyAsJson();

        LOGGER.info("post push: "+request);

        DeliveryOptions options = new DeliveryOptions().addHeader("action", "post-notif");

        vertx.eventBus().send(epimDbQueue,request, options, reply -> {
            if (reply.succeeded()) {
                JsonObject body = (JsonObject) reply.result().body();
                context.response().putHeader("Content-Type", "application/json");
                context.response().end(body.encode());

            } else {
                context.fail(reply.cause());
            }
        });

    }

    private void postReport(RoutingContext context) {
        JsonObject request = context.getBodyAsJson();
        JsonObject jsonData = request.getJsonObject("data");

       // LOGGER.info("jsondata: "+jsonData);
        String imgBase64 = jsonData.getString("evidence");
        String unor = jsonData.getString("org");
        String pegaNip = jsonData.getString("username");

        LOGGER.info("json: "+unor+"/"+pegaNip);

        Date date = new Date();
        LocalDate localDate = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        int year  = localDate.getYear();
        int month = localDate.getMonthValue();
        int day   = localDate.getDayOfMonth();


        String random = randomAlphaNumeric(8);

        LOGGER.info("random: "+random);

        String fileName = unor+pegaNip+year+month+day+"_"+random+".png";

        String filePath = fileStore+pegaNip+"/"+fileName;


        File file = new File(filePath);

        // add filename attribute to json
        request.put("filename", fileName);

        //LOGGER.info("newRequest before: "+request);

        // use blocking handler for handle blocking process
        vertx.<String>executeBlocking(future -> {

            File fileDirectory = new File(fileStore+pegaNip);

            if (!fileDirectory.exists()){
                fileDirectory.mkdir();
                LOGGER.info("directory created");
            }

            // decode base64 encoded image

            try (FileOutputStream imageOutFile = new FileOutputStream(file)) {



                if (!file.exists()){
                    file.createNewFile();
                }
                // Converting a Base64 String into Image byte array
                byte[] imageByteArray = Base64.getDecoder().decode(imgBase64);

                imageOutFile.write(imageByteArray);
            } catch (FileNotFoundException e) {
                LOGGER.info("Image not found "+e);
            } catch (IOException ioe) {
                LOGGER.info("Exception while reading the Image " + ioe);
            }

            String result="succeed";

            future.complete(result);

        }, res -> {

            if (res.succeeded()) {
              //  LOGGER.info("file created");

            //    LOGGER.info("newRequest:"+request);

                DeliveryOptions options = new DeliveryOptions().addHeader("action", "post-report");

                vertx.eventBus().send(epimDbQueue,request, options, reply -> {
                    if (reply.succeeded()) {
                        JsonObject body = (JsonObject) reply.result().body();
                        context.response().putHeader("Content-Type", "application/json");
                        context.response().end(body.encode());

                    } else {
                        context.fail(reply.cause());
                    }
                });

            } else {
                res.failed();
                //context.fail(reply.cause());
            }
        });


    }

    private void getGraphMail(RoutingContext context) {
        LOGGER.info("getGraphMail");

        String pegaNip = context.request().getParam("pegaNip");
        JsonObject request = new JsonObject().put("pegaNip", pegaNip);

        LOGGER.info("pegaNip: "+pegaNip);

        DeliveryOptions options = new DeliveryOptions().addHeader("action", "get-graph-mail");

        vertx.eventBus().send(epimDbQueue,request, options, reply -> {
            if (reply.succeeded()) {
                JsonObject body = (JsonObject) reply.result().body();
                context.response().putHeader("Content-Type", "application/json");
                context.response().end(body.encode());

            } else {
                context.fail(reply.cause());
            }
        });
    }

    private void disposeStaff(RoutingContext context) {
        JsonObject request = context.getBodyAsJson();

        DeliveryOptions options = new DeliveryOptions().addHeader("action", "post-delete-staff");

        vertx.eventBus().send(epimDbQueue,request, options, reply -> {
            if (reply.succeeded()) {
                JsonObject body = (JsonObject) reply.result().body();
                context.response().putHeader("Content-Type", "application/json");
                context.response().end(body.encode());

            } else {
                context.fail(reply.cause());
            }
        });
    }


    private void getEmpTree(RoutingContext context) {
        String pegaId = context.request().getParam("pegaId");
        JsonObject request = new JsonObject().put("pegaId", pegaId);

        DeliveryOptions options = new DeliveryOptions().addHeader("action", "get-emp-tree");
        vertx.eventBus().send(epimDbQueue, request, options, reply -> {
            if (reply.succeeded()) {
                JsonObject body = (JsonObject) reply.result().body();
                context.response().putHeader("Content-Type", "application/json");
                context.response().end(body.encodePrettily().replaceAll("\\\\", "").replaceAll("\"emp\" : \\[ \\{", "")
                        .replaceAll("\"data\" : \"", "\"data\" : ").replaceAll("}]}]\"", "}]}]")
                        .replaceAll("children1","children")
                        .replaceAll("children2", "children")
                        .replaceAll("children3", "children")
                        .replaceAll("\\} \\]", "").replaceAll("\"children\":null\\}\\]\"", "\"children\":null\\}\\]"));
            } else {
                context.fail(reply.cause());
            }
        });
    }

    private void getMailTo(RoutingContext context) {
        Integer mailId = Integer.parseInt(context.request().getParam("mailId"));
        JsonObject request = new JsonObject().put("mailId", mailId);

        DeliveryOptions options = new DeliveryOptions().addHeader("action", "get-mail-to");
        vertx.eventBus().send(epimDbQueue, request, options, reply -> {
            if (reply.succeeded()) {
                JsonObject body = (JsonObject) reply.result().body();
                context.response().putHeader("Content-Type", "application/json");
                context.response().end(body.encodePrettily().replaceAll("\\\\","")
                 .replaceAll("\"mail\" : \\[ \\{","")
                        .replaceFirst("\"\\[\"","[")
                        .replaceFirst("\"\\]\"","]")
                        .replaceAll("\"staff\":null","\"staff\":\\[\\]"));
            } else {
                context.fail(reply.cause());
            }
        });
    }

    private void getMail(RoutingContext context) {

        LOGGER.info("/getMail");
        String username = context.request().getParam("username");
        String mailType = context.request().getParam("mailType");
        LOGGER.info("username getMail :"+username+"/"+mailType);


        JsonObject request = new JsonObject().put("username", username).put("mailType",mailType);


        DeliveryOptions options = new DeliveryOptions().addHeader("action", "get-mail");
        vertx.eventBus().send(epimDbQueue, request, options, reply -> {
            if (reply.succeeded()) {
                JsonObject body = (JsonObject) reply.result().body();
                context.response().putHeader("Content-Type", "application/json");
                context.response().end(body.encodePrettily());
                LOGGER.info(body.encodePrettily()
                        .replaceFirst("\"mail\" : \\[","\"mail\":")
                        .replaceFirst("]}","}"));
            } else {
                LOGGER.info("route get mail failed");
                context.fail(reply.cause());
            }
        });
    }




    private void disposisiTo(RoutingContext context) {

        JsonObject request = context.getBodyAsJson();

        DeliveryOptions options = new DeliveryOptions().addHeader("action", "post-disposisi");

        vertx.eventBus().send(epimDbQueue,request, options, reply -> {
            if (reply.succeeded()) {
                JsonObject body = (JsonObject) reply.result().body();
                context.response().putHeader("Content-Type", "application/json");
                context.response().end(body.encode());

            } else {
                context.fail(reply.cause());
            }
        });
    }




    private void loginHandler(RoutingContext context) {
        JsonObject credential = context.getBodyAsJson();
       // System.out.println("login credential:" + credential.getString("username"));

        JsonObject request = new JsonObject().put("session", credential);

        String profileImg = credential.getString("username");

        DeliveryOptions options = new DeliveryOptions().addHeader("action", "get-user-login");

        vertx.eventBus().send(epimDbQueue,request, options, reply -> {
            if (reply.succeeded()) {

                String token = jwtAuth.generateToken(
                        new JsonObject()
                                .put("token",context.request().getHeader("login")),
                        new JWTOptions().setExpiresInMinutes((int) 200L)
                );


                JsonObject body = (JsonObject)reply.result().body();


                if (!body.getBoolean("succeed")){


                    body.put("succeed",false);
                    // body.remove("db-reply");
                    context.response().putHeader("Content-Type", "application/json");

                    context.response().end(body.encodePrettily());
                } else{
                    // LOGGER.info("pathImage : "+ avatarPath+ profileImg + ".png");
                    body.put("token",token);
                  //   body.put("avatar","http://localhost:8085/eoffice/public/images/avatar.png");
                  //  body.put("succeed","true");
                    context.response().putHeader("Content-Type", "application/json");

                    context.response().end(body.encodePrettily().replaceAll("\\\\","")
                            .replaceAll("\\\\","")
                            .replaceFirst("\"\\[","[")
                            .replaceFirst("\"data\" : \\[","\"data\":")
                            .replaceFirst("],",",")
                            .replaceFirst("\"\\]\"","]")
                            .replaceAll("}]}]\"","}]}]")
                            .replaceAll("\\} \\]", ""));

                    LOGGER.info("profile: "+body.encodePrettily().replaceAll("\\\\","")
                            .replaceAll("\\\\","")
                            .replaceFirst("\"\\[","[")
                            .replaceFirst("\"data\": \\[","\"data\":")
                            .replaceFirst("\"\\]\"","]")
                            .replaceFirst("],",",")
                            .replaceAll("}]}]\"","}]}]")
                            .replaceAll("\\} \\]", ""));

                }





            } else {
                context.fail(reply.cause());
            }
        });
    }


    private String randomAlphaNumeric(int count) {
        StringBuilder builder = new StringBuilder();
        while (count-- != 0) {
            int character = (int)(Math.random()*ALPHA_NUMERIC_STRING.length());
            builder.append(ALPHA_NUMERIC_STRING.charAt(character));
        }
        return builder.toString();
    }


}
