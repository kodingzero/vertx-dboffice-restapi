package exa.vertx.office;

import exa.vertx.util.Runner;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainVerticle extends AbstractVerticle {

    //private static final Logger LOGGER = LoggerFactory.getLogger(exa.vertx.pim.MainVerticle.class);

    public static void main(String[] args) {
        Runner.runExample(MainVerticle.class);
    }

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        Future<String> dbVerticleDeployment = Future.future();
        vertx.deployVerticle(new OfficeDatabaseVerticle(), dbVerticleDeployment.completer());


        dbVerticleDeployment.compose(id -> {

            Future<String> httpVerticleDeployment = Future.future();
            vertx.deployVerticle(
                    "exa.vertx.office.HttpServerVerticle",
                    new DeploymentOptions().setInstances(1),
                    httpVerticleDeployment.completer());

            return httpVerticleDeployment;

        }).setHandler(ar -> {
            if (ar.succeeded()) {
                startFuture.complete();
            } else {
                startFuture.fail(ar.cause());
            }
        });
    }
}
