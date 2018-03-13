package exa.vertx.pim;


import exa.vertx.util.Runner;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.impl.crypto.MacProvider;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTOptions;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.JWTAuthHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

public class MainVerticle extends AbstractVerticle {
    // tag::sql-fields[]
    private static final String SQL_GET_LOGIN = "SELECT nip,email,name, password  FROM pegawai where nip=?";

    private static final String SQL_GET_USER = "SELECT email,name, password FROM pegawai where nip=?";

    private static final String SQL_INBOX = "SELECT  sm.id,no_surat, kl.name klasifikasi,  js.nama_jenis_surat,\n" +
            "                               no_referensi,perihal, tgl_surat, tgl_diterima, \n" +
            "                               is_view,  p.nama created_by,  id_instansi_tujuan,alamat_tujuan\n" +
            "                         FROM surat_masuk sm, klasifikasi kl, jenis_surat js, tbl_pegawai p\n" +
            "                          where sm.id_klasifikasi=kl.id\n" +
            "                          and sm.id_jenis_surat=js.id\n" +
            "                          and sm.created_by=p.id\n" +
            "                          order by sm.id desc";
    private static final String SQL_OUTBOX ="SELECT  sm.id,no_surat, kl.name klasifikasi,  js.nama_jenis_surat,\n" +
            "                   no_referensi,perihal, tgl_surat, \n" +
            "                     p.nama created_by,  il.nama_unor\n" +
            "              FROM surat_keluar sm, klasifikasi kl, jenis_surat js, tbl_pegawai p, tbl_unor il\n" +
            "              where sm.id_klasifikasi=kl.id\n" +
            "              and sm.id_jenis_surat=js.id\n" +
            "              and sm.created_by=p.id\n" +
            "              and cast(sm.id_tujuan as text)=il.id\n" +
            "              order by sm.id desc";
    private static final String SQL_DISPOSISI = "SELECT d.id, id_surat,js.nama_jenis_surat surat, id_tujuan, il.name tujuan,id_pengirim,\n" +
            "no_agenda, isi_disposisi, \n" +
            "       is_view, is_tindak_lanjut, d.created_by, d.created_at, d.updated_at, \n" +
            "       ts.name sifat_surat, tindakan, tgl_disposisi, id_file, id_note, n.pesan note\n" +
            "         FROM disposisi d, jenis_surat js, instansi_luar il, tipe_surat ts, note n\n" +
            "  where d.id_surat=js.id\n" +
            "  and d.id_tujuan=il.id\n" +
            "  and d.sifat_surat=ts.id\n" +
            "  and d.id_note = n.id " +
            "  order by id desc";

    private static final String SQL_INBOX_BY_ID ="SELECT sm.id, id_asal,tu.nama_unor id_asal_unor, \n" +
            "id_tujuan,tuj.nama_unor unor_tujuan_unor, \n" +
            "no_surat, id_klasifikasi,k.name klasifikasi, \n" +
            "id_jenis_surat,nama_jenis_surat, \n" +
            "       no_urut, no_referensi, id_tipe, perihal, tgl_surat, tgl_diterima, \n" +
            "       id_file, is_disposisi, is_view, sm.created_by, sm.created_at, sm.updated_at, \n" +
            "       pengelola_akhir, id_instansi_tujuan, id_asal_luar, alamat_tujuan\n" +
            "  FROM surat_masuk sm\n" +
            "  LEFT JOIN tbl_unor tu ON tu.id= cast(sm.id_asal_luar as text)\n" +
            "  LEFT JOIN tbl_unor tuj ON tuj.id= cast(sm.id_tujuan as text)\n" +
            "  LEFT JOIN klasifikasi k ON k.id= sm.id_klasifikasi\n" +
            "  LEFT JOIN jenis_surat js ON js.id= sm.id_jenis_surat\n" +
            "  WHERE sm.created_by = cast(? as integer)\n" +
            "  ";

    private static final String SQL_OUTBOX_BY_ID="SELECT sk.id, id_asal, id_tujuan,tu.nama_unor unor_asal,tuj.nama_unor unor_tujuan, no_surat, \n" +
            "id_klasifikasi,k.name klasifikasi, \n" +
            "id_jenis_surat, nama_jenis_surat,\n" +
            "       no_referensi, id_tipe, id_sifat, perihal, isi_surat, tgl_surat, \n" +
            "       id_file, is_approved, sk.created_by, sk.created_at, sk.updated_at, alamat_tujuan\n" +
            "  FROM surat_keluar sk\n" +
            "  LEFT JOIN tbl_unor tu ON tu.id= cast(sk.id_asal as text)\n" +
            "  LEFT JOIN tbl_unor tuj ON tuj.id= cast(sk.id_tujuan as text)\n" +
            "  LEFT JOIN klasifikasi k ON k.id= sk.id_klasifikasi\n" +
            "  LEFT JOIN jenis_surat js ON js.id= sk.id_jenis_surat\n" +
            "  WHERE sk.created_by=cast(? as integer)\n";

    private static final String SQL_DASHBOARD ="select 'surat masuk' category,count(1) total from surat_masuk where created_by = cast(? as integer)\n" +
            "union\n" +
            "select 'surat keluar' category,count(1) total from surat_keluar where created_by =cast(? as integer)\n" +
            "union\n" +
            "select 'disposisi' category,count(1) total from disposisi where created_by =cast(? as integer)";

    private static final String SQL_DISPOSISI_BY_ID="SELECT ds.id, id_surat, sm.no_surat,sm.perihal, ds.id_tujuan, tu.nama_unor nama_tujuan,\n" +
            "id_pengirim, tp.nama_unor pengirim,p.nama created, p.nip no_agenda, isi_disposisi, \n" +
            "ds.is_view, is_tindak_lanjut, ds.created_by, ds.created_at, ds.updated_at, \n" +
            "sifat_surat, tindakan, tgl_disposisi, ds.id_file, id_note\n" +
            "FROM disposisi ds\n" +
            "LEFT JOIN tbl_unor tu ON tu.id= cast(ds.id_tujuan as text)\n" +
            "LEFT JOIN tbl_unor tp ON tp.id= cast(ds.id_pengirim as text)\n" +
            "LEFT JOIN tbl_pegawai p on p.id = ds.created_by\n" +
            "LEFT JOIN surat_masuk sm ON sm.id= ds.id_surat\n" +
            "WHERE ds.created_by=cast(? as integer)\n" +
            "order by id, id_surat";



    // tag::db-and-logger[]
    private JDBCClient dbClient;


    private static final Logger LOGGER = LoggerFactory.getLogger(MainVerticle.class);

    public static void main(String[] args) {
        Runner.runExample(MainVerticle.class);
    }

    @Override
    public void start(Future<Void> startFuture) throws Exception {

        Future<Void> steps = prepareDatabase().compose(v -> startHttpServer());
        steps.setHandler(startFuture.completer());

    }

    private Future<Void> startHttpServer() {
        Future<Void> future = Future.future();
        HttpServer server = vertx.createHttpServer();

        Router router = Router.router(vertx);

        Router apiRouter = Router.router(vertx);
        Set<String> allowHeaders = new HashSet<>();
        allowHeaders.add("x-requested-with");
        allowHeaders.add("Access-Control-Allow-Origin");
        allowHeaders.add("origin");
        allowHeaders.add("Content-Type");
        allowHeaders.add("accept");
        Set<HttpMethod> allowMethods = new HashSet<>();
        allowMethods.add(HttpMethod.GET);
        allowMethods.add(HttpMethod.POST);
        allowMethods.add(HttpMethod.DELETE);
        allowMethods.add(HttpMethod.PATCH);

        router.route().handler(CorsHandler.create("*")
                .allowedHeaders(allowHeaders)
                .allowedMethods(allowMethods));
        router.route().handler(BodyHandler.create());

        //router.post("/login").handler(this::loginHandler);


        apiRouter.get("/login/:username").handler(this::loginHandler);
        apiRouter.get("/logout").handler(this::logoutHandler);;

        apiRouter.get("/getInbox").handler(this::handleListInbox);
        apiRouter.get("/getInbox/:username").handler(this::handleListInboxById);
        apiRouter.get("/getOutbox").handler(this::handleListOutbox);
        apiRouter.get("/getOutbox/:username").handler(this::handleOutboxById);
        apiRouter.get("/getDisposisi").handler(this::handleListDisposisi);
        apiRouter.get("/getDisposisi/:username").handler(this::handleDisposisiById);
        apiRouter.get("/getDashboard/:username").handler(this::handleDashboard);


        router.mountSubRouter("/e-office/api", apiRouter);



        server.requestHandler(router::accept)
                .listen(8018, ar -> {
                    if (ar.succeeded()) {
                        LOGGER.info("HTTP Server running on port 8018");
                    } else {
                        LOGGER.error("Could not start a HTTP Server", ar.cause());
                        future.fail(ar.cause());
                    }
                });

        return future;
    }

    private void logoutHandler(RoutingContext routingContext) {

            System.out.println("logout");
        routingContext.clearUser();
        routingContext.response()
                    .setStatusCode(302)
                    .end();

    }

    private void loginHandler(RoutingContext routingContext) {
        String userName = routingContext.request().getParam("username");
        HttpServerResponse response = routingContext.response();
        System.out.println("loginhandler : "+userName);


        if (userName == null){
            sendError(400, response);
        } else{
            dbClient.getConnection(car -> {
                if (car.succeeded()) {
                    SQLConnection connection = car.result();
                    connection.queryWithParams(SQL_GET_LOGIN,new JsonArray().add(userName), (AsyncResult<ResultSet> res) -> {
                        connection.close();

                        if (res.succeeded()) {
                            JsonArray arr = new JsonArray();
                            res.result().getRows().forEach(arr::add);
                            routingContext.response().putHeader("content-type", "application/json").end(arr.encode());
                            //String result = null;
                            /*long nowMillis = System.currentTimeMillis();
                            Date now = new Date(nowMillis);
                            JwtBuilder builder = Jwts.builder()
                                    .setId(userName)
                                    .setIssuedAt(now)
                                    .setAudience(userName);
                            String compact = builder.signWith(SignatureAlgorithm.HS256, key).compact();
                            System.out.println("compact : "+compact);
                            routingContext.response().end(compact);*/

                        } else{
                            routingContext.response().setStatusCode(500);
                            routingContext.response().putHeader("Content-Type", "application/json");
                            routingContext.response().end(new JsonObject()
                                    .put("success", false)
                                    .put("error", res.cause().getMessage()).encode());
                        }
                    });
                }
            });
        }
    }


    private void handleDisposisiById(RoutingContext routingContext) {
        String userName = routingContext.request().getParam("username");
        HttpServerResponse response = routingContext.response();

        LOGGER.info("username : "+userName);

        if (userName == null){
            sendError(400, response);
        } else{
            dbClient.getConnection(car -> {
                if (car.succeeded()) {
                    SQLConnection connection = car.result();
                    connection.queryWithParams(SQL_DISPOSISI_BY_ID,new JsonArray().add(userName), (AsyncResult<ResultSet> res) -> {
                        connection.close();

                        if (res.succeeded()) {
                            JsonArray arr = new JsonArray();
                            res.result().getRows().forEach(arr::add);
                            routingContext.response().putHeader("content-type", "application/json").end(arr.encode());

                        } else{
                            routingContext.response().setStatusCode(500);
                            routingContext.response().putHeader("Content-Type", "application/json");
                            routingContext.response().end(new JsonObject()
                                    .put("success", false)
                                    .put("error", res.cause().getMessage()).encode());
                        }
                    });
                }
            });
        }
    }

    private void handleOutboxById(RoutingContext routingContext) {
        String userName = routingContext.request().getParam("username");
        HttpServerResponse response = routingContext.response();

        LOGGER.info("username : "+userName);

        if (userName == null){
            sendError(400, response);
        } else{
            dbClient.getConnection(car -> {
                if (car.succeeded()) {
                    SQLConnection connection = car.result();
                    connection.queryWithParams(SQL_OUTBOX_BY_ID,new JsonArray().add(userName), (AsyncResult<ResultSet> res) -> {
                        connection.close();

                        if (res.succeeded()) {
                            JsonArray arr = new JsonArray();
                            res.result().getRows().forEach(arr::add);
                            routingContext.response().putHeader("content-type", "application/json").end(arr.encode());

                        } else{
                            routingContext.response().setStatusCode(500);
                            routingContext.response().putHeader("Content-Type", "application/json");
                            routingContext.response().end(new JsonObject()
                                    .put("success", false)
                                    .put("error", res.cause().getMessage()).encode());
                        }
                    });
                }
            });
        }
    }

    private void handleDashboard(RoutingContext routingContext) {
        String userName = routingContext.request().getParam("username");
        HttpServerResponse response = routingContext.response();

        LOGGER.info("username : "+userName);

        if (userName == null){
            sendError(400, response);
        } else{
            dbClient.getConnection(car -> {
                if (car.succeeded()) {
                    SQLConnection connection = car.result();
                    connection.queryWithParams(SQL_DASHBOARD,new JsonArray().add(userName).add(userName).add(userName), (AsyncResult<ResultSet> res) -> {
                        connection.close();

                        if (res.succeeded()) {
                            JsonArray arr = new JsonArray();
                            res.result().getRows().forEach(arr::add);
                            routingContext.response().putHeader("content-type", "application/json").end(arr.encode());

                        } else{
                            routingContext.response().setStatusCode(500);
                            routingContext.response().putHeader("Content-Type", "application/json");
                            routingContext.response().end(new JsonObject()
                                    .put("success", false)
                                    .put("error", res.cause().getMessage()).encode());
                        }
                    });
                }
            });
        }
    }

    private void handleListInboxById(RoutingContext routingContext) {
        String userName = routingContext.request().getParam("username");
        HttpServerResponse response = routingContext.response();

        LOGGER.info("username : "+userName);

        if (userName == null){
            sendError(400, response);
        } else{
            dbClient.getConnection(car -> {
                if (car.succeeded()) {
                    SQLConnection connection = car.result();
                    connection.queryWithParams(SQL_INBOX_BY_ID,new JsonArray().add(userName), (AsyncResult<ResultSet> res) -> {
                        connection.close();

                        if (res.succeeded()) {
                            JsonArray arr = new JsonArray();
                            res.result().getRows().forEach(arr::add);
                            routingContext.response().putHeader("content-type", "application/json").end(arr.encode());

                        } else{
                            routingContext.response().setStatusCode(500);
                            routingContext.response().putHeader("Content-Type", "application/json");
                            routingContext.response().end(new JsonObject()
                                    .put("success", false)
                                    .put("error", res.cause().getMessage()).encode());
                        }
                    });
                }
            });
        }
    }

    private void sendError(int statusCode, HttpServerResponse response) {
        response.setStatusCode(statusCode).end();
    }

    private void handleAuth(RoutingContext routingContext) {
        String userName = routingContext.request().getParam("username");
        HttpServerResponse response = routingContext.response();

       LOGGER.info("username : "+userName);

        if (userName == null){
            sendError(400, response);
        } else{
            dbClient.getConnection(car -> {
                if (car.succeeded()) {
                    SQLConnection connection = car.result();
                    connection.queryWithParams(SQL_GET_USER ,new JsonArray().add(userName), (AsyncResult<ResultSet> res) -> {
                        connection.close();

                        if (res.succeeded()) {
                            JsonArray arr = new JsonArray();
                            res.result().getRows().forEach(arr::add);
                            routingContext.response().putHeader("content-type", "application/json").end(arr.encode());

                        } else{
                            routingContext.response().setStatusCode(500);
                            routingContext.response().putHeader("Content-Type", "application/json");
                            routingContext.response().end(new JsonObject()
                                    .put("success", false)
                                    .put("error", res.cause().getMessage()).encode());
                        }
                    });
                }
            });
        }


    }

    private void handleListDisposisi(RoutingContext routingContext) {
        dbClient.getConnection(car -> {
            if (car.succeeded()) {
                SQLConnection connection = car.result();
                connection.query(SQL_DISPOSISI, (AsyncResult<ResultSet> res) -> {
                    connection.close();

                    if (res.succeeded()) {
                        JsonArray arr = new JsonArray();
                        res.result().getRows().forEach(arr::add);
                        routingContext.response().putHeader("content-type", "application/json").end(arr.encode());

                    }else{
                        routingContext.response().setStatusCode(500);
                        routingContext.response().putHeader("Content-Type", "application/json");
                        routingContext.response().end(new JsonObject()
                                .put("success", false)
                                .put("error", res.cause().getMessage()).encode());
                    }
                });
            }
        });
    }

    private void handleListOutbox(RoutingContext routingContext) {
        dbClient.getConnection(car -> {
            if (car.succeeded()) {
                SQLConnection connection = car.result();
                connection.query(SQL_OUTBOX, (AsyncResult<ResultSet> res) -> {
                    connection.close();

                    if (res.succeeded()) {
                        JsonArray arr = new JsonArray();
                        res.result().getRows().forEach(arr::add);
                        routingContext.response().putHeader("content-type", "application/json").end(arr.encode());
                    }else{
                        routingContext.response().setStatusCode(500);
                        routingContext.response().putHeader("Content-Type", "application/json");
                        routingContext.response().end(new JsonObject()
                                .put("success", false)
                                .put("error", res.cause().getMessage()).encode());
                    }
                });
            }
        });
    }

    private void handleListInbox(RoutingContext routingContext) {
        dbClient.getConnection(car -> {
            if (car.succeeded()) {
                SQLConnection connection = car.result();
                connection.query(SQL_INBOX, (AsyncResult<ResultSet> res) -> {
                    connection.close();

                    if (res.succeeded()) {
                       JsonArray arr = new JsonArray();
                       res.result().getRows().forEach(arr::add);
                       routingContext.response().putHeader("content-type", "application/json").end(arr.encode());
                    } else{
                        routingContext.response().setStatusCode(500);
                        routingContext.response().putHeader("Content-Type", "application/json");
                        routingContext.response().end(new JsonObject()
                                .put("success", false)
                                .put("error", res.cause().getMessage()).encode());
                    }
                });
            }
        });
    }

    private Future<Void> prepareDatabase() {
        Future<Void> future = Future.future();
        dbClient = JDBCClient.createShared(vertx, new JsonObject()
                .put("url", "jdbc:postgresql://localhost:5432/db_office")
                .put("user", "postgres")
                .put("password", "admin")
                .put("max_pool_size", 30)
        );

        dbClient.getConnection(ar -> {
            if (ar.failed()) {
                LOGGER.error("Could not open a database connection", ar.cause());
                future.fail(ar.cause());
            } else {
                future.complete();
                LOGGER.info("Connection established");

            }
        });

        return future;
    }


}
