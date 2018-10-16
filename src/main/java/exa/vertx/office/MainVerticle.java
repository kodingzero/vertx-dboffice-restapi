package exa.vertx.office;

import exa.vertx.util.Config;
import exa.vertx.util.Runner;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class MainVerticle extends AbstractVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(exa.vertx.office.MainVerticle.class);

    public static void main(String[] args) {
        Runner.runExample(MainVerticle.class);
    }

    @Override
    public void start(Future<Void> startFuture) throws Exception {

        final JsonObject config = Config.fromFile("src/config/config.json");

        CompositeFuture
                .all(deployHelper(HttpServerVerticle.class.getName(), new DeploymentOptions().setInstances(1).setConfig(config)),
                        deployHelper(EofficeDBVerticle.class.getName(),new DeploymentOptions().setConfig(config)),
                        deployHelper(FileSystemVerticle.class.getName(),new DeploymentOptions().setConfig(config)))
                .setHandler(result -> {
            if(result.succeeded()){
                startFuture.complete();
            } else {
                startFuture.fail(result.cause());
            }
        });
    }



    private Future<Void> deployHelper(String name,DeploymentOptions options){
        final JsonObject config = Config.fromFile("src/config/config.json");
        final Future<Void> future = Future.future();
        vertx.deployVerticle(name, options,res -> {
            if(res.failed()){
                LOGGER.info("Failed to deploy verticle " + name);
                future.fail(res.cause());
            } else {
                LOGGER.info("Deployed verticle " + name);
                future.complete();
            }
        });
        return future;
    }

   /* @Override
    public void start(Future<Void> startFuture) throws Exception {
        Future<String> dbVerticleDeployment = Future.future();
        Future<String> uploadVerticleDeployment = Future.future();

        final JsonObject config = Config.fromFile("src/config/config.json");

       //final JsonObject config = Config.fromFile("./config.json");
        //LOGGER.info("config:"+config);



       // vertx.deployVerticle(new OfficeDatabaseVerticle(),
       //         new DeploymentOptions().setConfig(config),
       //         dbVerticleDeployment.completer());


        vertx.deployVerticle(new EofficeDBVerticle(),
                new DeploymentOptions().setConfig(config),
                dbVerticleDeployment.completer());



        dbVerticleDeployment.compose(id -> {

            Future<String> httpVerticleDeployment = Future.future();
            vertx.deployVerticle(
                    "exa.vertx.office.HttpServerVerticle",
                    new DeploymentOptions().setInstances(1).setConfig(config),
                    httpVerticleDeployment.completer());


            return httpVerticleDeployment;

        }).setHandler(ar -> {
            if (ar.succeeded()) {
                startFuture.complete();
            } else {
                startFuture.fail(ar.cause());
            }
        });
    }*/
}
