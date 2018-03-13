package exa.vertx.office;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.JWTAuthHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class HttpServerVerticle extends AbstractVerticle {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpServerVerticle.class);

    public static final String CONFIG_HTTP_SERVER_PORT = "http.server.port";
    public static final String CONFIG_WIKIDB_QUEUE = "epimdb.queue";

    private String epimDbQueue = "epimdb.queue";

    @Override
    public void start(Future<Void> startFuture) throws Exception {

        epimDbQueue = config().getString(CONFIG_WIKIDB_QUEUE, "epimdb.queue");

        // tag::https-server[]
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

        JWTAuth jwtAuth = JWTAuth.create(vertx, new JsonObject()
                .put("keyStore", new JsonObject()
                        .put("path", "keystore.jceks")
                        .put("type", "jceks")
                        .put("password", "secret")));

      //  apiRouter.route().handler(JWTAuthHandler.create(jwtAuth, "/eoffice/api/login"));

        apiRouter.get("/token").handler(ctx -> {
            ctx.response().putHeader("Content-Type", "application/json");
            ctx.response().end(jwtAuth.generateToken(new JsonObject(), new JWTOptions().setExpiresInMinutes(12L)));
        });


        apiRouter.post("/login").handler(context -> {

            JsonObject credential = context.getBodyAsJson();
            System.out.println(credential);

            JsonObject request = new JsonObject().put("session", credential);

            DeliveryOptions options = new DeliveryOptions().addHeader("action", "get-user-login");

            vertx.eventBus().send(epimDbQueue,request, options, reply -> {
                if (reply.succeeded()) {

                    String token = jwtAuth.generateToken(
                            new JsonObject()
                                    .put("token",context.request().getHeader("login")),
                            new JWTOptions().setExpiresInMinutes(200L)
                    );


                    JsonObject body = (JsonObject)reply.result().body();

                    if (body.getString("db-reply").equalsIgnoreCase("user or password doesn't matched.")){
                        body.put("reply",body.getString("db-reply"));
                        body.remove("db-reply");
                        context.response().putHeader("Content-Type", "application/json");

                        context.response().end(body.encodePrettily().replaceAll("\\\\","")
                                .replaceAll("\\{\"profile\":\\[\\{\"data\" : \"","{\"data\":")
                                .replaceAll("}]}]\"","}]}]")
                                .replaceAll("\"data\" : \"","\"data\" : "));
                    } else{
                        LOGGER.info("reply :"+body.getString("db-reply"));
                        body.put("token",token);
                        body.put("reply",body.getString("db-reply"));
                        body.remove("db-reply");
                        context.response().putHeader("Content-Type", "application/json");
                        //context.response().end(body.encode());
                        context.response().end(body.encodePrettily().replaceAll("\\\\","")
                                .replaceAll("\\{\"profile\":\\[\\{\"data\" : \"","{\"data\":")
                                .replaceAll("}]}]\"","}]}]")
                                .replaceAll("\"data\" : \"","\"data\" : "));


                    }
                    //System.out.println("body : "+body.getString("db-reply"));




                } else {
                    context.fail(reply.cause());
                }
            });

        });

        // [start method get]

        apiRouter.get("/login/:username/:password").handler(this::loginHandler);
        apiRouter.get("/getDashboard/:nip").handler(this::dashbordHandler);
        apiRouter.get("/getInbox/:nip").handler(this::inboxHandler);
        apiRouter.get("/getFlowDispos/:idsurat").handler(this::flowDisposHandler);
        apiRouter.get("/getStaffLev1/:unor/:level").handler(this::getStaffDisposisiHandler);
        apiRouter.get("/getStaffLev2/:unor").handler(this::getStaffLev2Handler);
        apiRouter.get("/getStaffLev3/:unor").handler(this::getStaffLev3Handler);
        apiRouter.get("/getStaff/:unor").handler(this::staffDisposHandler);
        // [start new method, cause patching db]
        apiRouter.get("/getMail/:nip/:type").handler(this::getMail);
        apiRouter.get("/getMailTo/:mailId").handler(this::getMailTo);
        // [end tag method get]


        // [start method post]
        apiRouter.post("/postDisposNotif").handler(this::disposNotifHandler);
        apiRouter.post("/postTindakLanjut").handler(this::tindakLanjutHandler);
        // [end tag : method post]

        router.mountSubRouter("/eoffice/api", apiRouter);

        int portNumber = config().getInteger(CONFIG_HTTP_SERVER_PORT, 8085);  // <3>
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

    private void getMailTo(RoutingContext context) {
        String mailId = context.request().getParam("mailId");
        JsonObject request = new JsonObject().put("mailId", mailId);

        DeliveryOptions options = new DeliveryOptions().addHeader("action", "get-mail-to");
        vertx.eventBus().send(epimDbQueue, request, options, reply -> {
            if (reply.succeeded()) {
                JsonObject body = (JsonObject) reply.result().body();
                context.response().putHeader("Content-Type", "application/json");
                context.response().end(body.encode().replaceAll("\\\\","")
                        //.replaceAll("\"data\":\"","\"data\":")
                        .replaceAll("\\{\"mail\":\\[\\{\"data\":\"","{\"data\":")
                        .replaceAll("]\"}]}","]}"));
            } else {
                context.fail(reply.cause());
            }
        });
    }

    private void getMail(RoutingContext context) {

        String nip = context.request().getParam("nip");
        String type = context.request().getParam("type");
        JsonObject request = new JsonObject().put("nip", nip).put("type",type);

        DeliveryOptions options = new DeliveryOptions().addHeader("action", "get-mail");
        vertx.eventBus().send(epimDbQueue, request, options, reply -> {
            if (reply.succeeded()) {
                JsonObject body = (JsonObject) reply.result().body();
                context.response().putHeader("Content-Type", "application/json");
                context.response().end(body.encode().replaceAll("\\\\","")
                        //.replaceAll("\"data\":\"","\"data\":")
                        .replaceAll("\\{\"mail\":\\[\\{\"data\":\"","{\"data\":")
                        .replaceAll("]\"}]}","]}"));
            } else {
                context.fail(reply.cause());
            }
        });
    }

    private void tindakLanjutHandler(RoutingContext context) {
        JsonObject disposNotif = context.getBodyAsJson();
        JsonObject request = new JsonObject().put("data", disposNotif);

        DeliveryOptions options = new DeliveryOptions().addHeader("action", "post-tindak-lanjut");

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

    private void getStaffLev3Handler(RoutingContext context) {
        String unor = context.request().getParam("unor");

        JsonObject request = new JsonObject().put("unor", unor);


        DeliveryOptions options = new DeliveryOptions().addHeader("action", "get-staff-lev3");

        vertx.eventBus().send(epimDbQueue,request, options, reply -> {
            if (reply.succeeded()) {
                JsonObject body = (JsonObject) reply.result().body();
                context.response().putHeader("Content-Type", "application/json");
                context.response().end(body.encode().replaceAll("\\\\","")
                        //.replaceAll("\"data\":\"","\"data\":")
                        .replaceAll("\\{\"unor\":\\[\\{\"data\":\"","{\"data\":")
                        .replaceAll("]\"}]}","]}"));
            } else {
                context.fail(reply.cause());
            }
        });
    }

    private void getStaffLev2Handler(RoutingContext context) {
        String unor = context.request().getParam("unor");

        JsonObject request = new JsonObject().put("unor", unor);


        DeliveryOptions options = new DeliveryOptions().addHeader("action", "get-staff-lev2");

        vertx.eventBus().send(epimDbQueue,request, options, reply -> {
            if (reply.succeeded()) {
                JsonObject body = (JsonObject) reply.result().body();
                context.response().putHeader("Content-Type", "application/json");
                context.response().end(body.encode().replaceAll("\\\\","")
                        //.replaceAll("\"data\":\"","\"data\":")
                        .replaceAll("\\{\"unor\":\\[\\{\"data\":\"","{\"data\":")
                        .replaceAll("]\"}]}","]}"));
            } else {
                context.fail(reply.cause());
            }
        });
    }

    private void disposNotifHandler(RoutingContext context) {

        JsonObject disposNotif = context.getBodyAsJson();
        JsonObject request = new JsonObject().put("data", disposNotif);

        DeliveryOptions options = new DeliveryOptions().addHeader("action", "post-dispos-notif");

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

    private void staffDisposHandler(RoutingContext context) {
        String unor = context.request().getParam("unor");
        JsonObject request = new JsonObject().put("unor", unor);


        DeliveryOptions options = new DeliveryOptions().addHeader("action", "get-staff");

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

    private void getStaffDisposisiHandler(RoutingContext context) {
        String unor = context.request().getParam("unor");
        String level = context.request().getParam("level");
        JsonObject request = new JsonObject().put("unor", unor).put("level",level);



        DeliveryOptions options = new DeliveryOptions().addHeader("action", "get-staff-lev1");

        vertx.eventBus().send(epimDbQueue,request, options, reply -> {
            if (reply.succeeded()) {
                JsonObject body = (JsonObject) reply.result().body();
                context.response().putHeader("Content-Type", "application/json");
                context.response().end(body.encode().replaceAll("\\\\","")
                        //.replaceAll("\"data\":\"","\"data\":")
                        .replaceAll("\\{\"unor\":\\[\\{\"data\":\"","{\"data\":")
                        .replaceAll("]\"}]}","]}"));
            } else {
                context.fail(reply.cause());
            }
        });
    }

    private void flowDisposHandler(RoutingContext context) {
        String idSurat = context.request().getParam("idsurat");
        JsonObject request = new JsonObject().put("idsurat", idSurat);


        DeliveryOptions options = new DeliveryOptions().addHeader("action", "get-flow-dispos");

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

    private void dashbordHandler(RoutingContext context) {
        String nip = context.request().getParam("nip");
        JsonObject request = new JsonObject().put("nip", nip);


        DeliveryOptions options = new DeliveryOptions().addHeader("action", "get-dashboard");

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
        String userName = context.request().getParam("username");
        String password = context.request().getParam("password");

        System.out.println("login handler");


        JsonObject request = new JsonObject().put("username", userName)
                .put("password",password);

        DeliveryOptions options = new DeliveryOptions().addHeader("action", "get-user");

        vertx.eventBus().send(epimDbQueue,request, options, reply -> {
            if (reply.succeeded()) {


                JsonObject body = (JsonObject) reply.result().body();
                context.response().putHeader("Content-Type", "application/json");
                context.response().end(body.encode().replaceAll("\\[","")
                        .replaceAll("]",""));
            } else {
                context.fail(reply.cause());
            }
        });
    }

    private void inboxHandler(RoutingContext context) {

        String nip = context.request().getParam("nip");
        JsonObject request = new JsonObject().put("nip", nip);

        DeliveryOptions options = new DeliveryOptions().addHeader("action", "get-inbox-byuserid");
        vertx.eventBus().send(epimDbQueue, request, options, reply -> {
            if (reply.succeeded()) {
                JsonObject body = (JsonObject) reply.result().body();
                context.response().putHeader("Content-Type", "application/json");
                context.response().end(body.encode()
                        .replaceAll("\\\\","")
                        .replaceAll(":\"\\{",":{")
                        .replaceAll("\"\\},\\{\"data\"","},{\"data\""));
            } else {
                context.fail(reply.cause());
            }
        });
    }

    // (...)
    // end::start[]

    private void indexHandler(RoutingContext context) {

        DeliveryOptions options = new DeliveryOptions().addHeader("action", "get-test");

        vertx.eventBus().send(epimDbQueue, new JsonObject(), options, reply -> {
            if (reply.succeeded()) {
                JsonObject body = (JsonObject) reply.result().body();
                System.out.println("hasil : "+body.getJsonArray("test").getList());
            } else {
                context.fail(reply.cause());
            }
        });
    }
    // end::indexHandler[]
}
