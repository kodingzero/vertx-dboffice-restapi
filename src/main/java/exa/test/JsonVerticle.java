package exa.test;

import exa.vertx.pim.MainVerticle;
import exa.vertx.util.Runner;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class JsonVerticle extends AbstractVerticle {
    private static final Logger LOGGER = LoggerFactory.getLogger(JsonVerticle.class);

    private static final String SQL_GENRE = "SELECT id, name, COALESCE(get_children(id), '[]') AS children FROM genres where id=1";

    private JDBCClient dbClient;

    public static void main(String[] args) {
        Runner.runExample(JsonVerticle.class);
    }

    @Override
    public void  start(Future<Void> startFuture) throws Exception {
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

        apiRouter.get("/genres").handler(this::genreHandler);

        router.mountSubRouter("/api", apiRouter);


        server.requestHandler(router::accept)
                .listen(8085, ar -> {
                    if (ar.succeeded()) {
                        LOGGER.info("HTTP Server running on port 8085");
                    } else {
                        LOGGER.error("Could not start a HTTP Server", ar.cause());
                        future.fail(ar.cause());
                    }
                });

        return future;
    }

    private void genreHandler(RoutingContext routingContext) {

       /* dbClient.query(sqlQueries.get(SqlQuery.ALL_PAGES), res -> {
            if (res.succeeded()) {
                List<String> pages = res.result()
                        .getResults()
                        .stream()
                        .map(json -> json.getString(0))
                        .sorted()
                        .collect(Collectors.toList());
                message.reply(new JsonObject().put("pages", new JsonArray(pages)));
            } else {
                reportQueryError(message, res.cause());
            }
        });*/
        dbClient.getConnection(car -> {
            if (car.succeeded()) {
                SQLConnection connection = car.result();
                connection.query(SQL_GENRE, (AsyncResult<ResultSet> res) -> {
                    connection.close();

                    if (res.succeeded()) {

                       /* JsonArray pages = new JsonArray(res.result()
                                .getResults()
                                .stream()
                                .map(json -> json.getString(2))
                                .sorted()
                                .collect(Collectors.toList()));*/

                        /*routingContext.response().putHeader("content-type","application/json; charset=utf-8")
                                .end(res.result().getResults().get(0).getString(2));*/
                        routingContext.response().putHeader("content-type","application/json; charset=utf-8")
                                .end();

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

    private void sendError(int statusCode, HttpServerResponse response) {
        response.setStatusCode(statusCode).end();
    }


    private Future<Void> prepareDatabase() {
        Future<Void> future = Future.future();
        dbClient = JDBCClient.createShared(vertx, new JsonObject()
                .put("url", "jdbc:postgresql://localhost:5438/json_test")
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
