package io.radicalbit.nsdb.core

import akka.actor._
import akka.stream.ActorMaterializer
import io.radicalbit.nsdb.actors.DatabaseActorsGuardian
import io.radicalbit.nsdb.api.Api
import io.radicalbit.nsdb.web.StaticResources

trait Core {
  protected implicit def system: ActorSystem
}

trait WebCore extends Core {
  protected implicit def materializer: ActorMaterializer
}

trait BootedCore extends Core with Api with StaticResources {
  override implicit def system = ActorSystem("ignorantodb")
}

trait WebBootedCore extends BootedCore with WebCore {
  override implicit def materializer = ActorMaterializer()

  implicit val executionContext = system.dispatcher
}

trait CoreActors { this: Core =>
  // define actors here
  lazy val guardian = system.actorOf(DatabaseActorsGuardian.props, "guardian")
}