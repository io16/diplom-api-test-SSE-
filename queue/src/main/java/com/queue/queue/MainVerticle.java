package com.queue.queue;


import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.subjects.PublishSubject;
import io.vertx.core.Handler;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.http.HttpServer;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.sql.SQLClient;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.handler.CorsHandler;

import java.util.Set;

import static io.vertx.core.http.HttpMethod.POST;

public class MainVerticle extends AbstractVerticle implements Handler<HttpServerRequest> {
    HttpServer httpServer;
    public SQLClient mySqlClient;
    private static final String query = "SELECT email, password_salt, password_hash FROM users where id = ? ";

    public static void main(String[] args) {
        Vertx v = Vertx.vertx(new VertxOptions()
            .setPreferNativeTransport(true)
        );
        System.out.println("Native transport: " + v.isNativeTransportEnabled());
        v.deployVerticle(MainVerticle.class.getName());
    }

    Router router;

    @Override
    public void start() throws Exception {
        router = Router.router(vertx);
        HttpServerOptions options = new HttpServerOptions()
            .setHost("0.0.0.0")
            .setPort(8080);
        httpServer = vertx.createHttpServer(options)
            .requestHandler(this::handle)
            .listen(res -> {
                if (res.succeeded()) {
                    System.out.println("HTTP server is listening on {}:{} 0.0.0.0 8080");
                } else {
                    System.out.println("Failed listening http server");
                }
            });
    }

    @Override
    public void handle(HttpServerRequest httpServerRequest) {

        router.accept(httpServerRequest);
        attachRoutes();
    }

    private CorsHandler getCorsHandler() {
        var corsHandler = CorsHandler.create("*");
        corsHandler.allowedHeaders(Set.of(
            "x-requested-with",
            "Access-Control-Allow-Origin",
            "origin",
            "Content-Type",
            "accept"
        ));
        corsHandler.getDelegate().allowedMethods(Set.of(
            HttpMethod.GET,
            POST,
            HttpMethod.DELETE,
            HttpMethod.PUT,
            HttpMethod.PATCH,
            HttpMethod.OPTIONS
        ));
        return corsHandler;
    }

    private void attachRoutes() {
        router.route().handler(getCorsHandler());
//        router.route().handler(BodyHandler.create());
//        router.post("/api/v1/test").handler(this::generateJwt);

        router.get("/events").handler(ctx -> {

            ctx.response().setChunked(true);

            ctx.response().headers().add("Content-Type", "text/event-stream;charset=UTF-8");
            ctx.response().headers().add("Connection", "keep-alive");

            vertx.eventBus().consumer("events", msg -> {

                System.out.println("start");

                ctx.response().write("event: message\ndata: " + msg.body() + "\n\n");
            });
        });

        vertx.createHttpServer().requestHandler(router::accept).listen(8080);

        vertx.setPeriodic(1000, time -> {
            vertx.eventBus().publish("events", "Hello World " + System.currentTimeMillis());
        });

        router.get("/sse").handler(event -> {
            event.response().setChunked(true);
            event.response().putHeader("Content-Type", "text/event-stream");
            event.response().putHeader("Connection", "keep-alive");
            event.response().putHeader("Cache-Control", "no-cache");

            var subject = PublishSubject.create();
            subject.onNext("Hellosubject");

            var o = Observable.create(e -> {
                e.onNext("2");
            });

            var publishSubject = PublishSubject.create();
            var flowableFromSubject = publishSubject.toFlowable(BackpressureStrategy.MISSING);

            var f = Flowable.create(e -> {
                e.onNext("Hello");
                e.onNext("Hello1");
                e.onNext("Hello2");
                e.onNext("Hello3");
//                e.onComplete();
            }, BackpressureStrategy.BUFFER);

//            if (!event.response().closed()) {
            System.out.println("1");
            flowableFromSubject.subscribe(
                e -> {
                    event.response().write(e.toString());
                }
            );

//            }
            for (int i = 0; i < 1000; i++) {
                publishSubject.onNext("dsadasdas\n");
                publishSubject.onNext(2);
            }

            publishSubject.onComplete();
        });
    }
}

