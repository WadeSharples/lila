package lila.team

import org.joda.time.{ DateTime, Period }
import reactivemongo.akkastream.cursorProducer
import reactivemongo.api._
import reactivemongo.api.bson._

import lila.db.dsl._
import lila.hub.LeaderTeam
import lila.user.User

final class TeamRepo(val coll: Coll)(implicit ec: scala.concurrent.ExecutionContext) {

  import BSONHandlers._

  private val lightProjection = $doc("name" -> true).some

  def byId(id: Team.ID) = coll.byId[Team](id)

  def byOrderedIds(ids: Seq[Team.ID]) = coll.byOrderedIds[Team, Team.ID](ids)(_.id)

  def byLeader(id: Team.ID, leaderId: User.ID): Fu[Option[Team]] =
    coll.one[Team]($id(id) ++ $doc("leaders" -> leaderId))

  def lightsByLeader(leaderId: User.ID): Fu[List[LeaderTeam]] =
    coll
      .find($doc("leaders" -> leaderId) ++ enabledSelect, lightProjection)
      .sort(sortPopular)
      .cursor[LeaderTeam](ReadPreference.secondaryPreferred)
      .list(100)

  def enabled(id: Team.ID) = coll.one[Team]($id(id) ++ enabledSelect)

  def byIdsSortPopular(ids: Seq[Team.ID]): Fu[List[Team]] =
    coll
      .find($inIds(ids))
      .sort(sortPopular)
      .cursor[Team](ReadPreference.secondaryPreferred)
      .list(100)

  def enabledTeamsByLeader(userId: User.ID): Fu[List[Team]] =
    coll
      .find($doc("leaders" -> userId) ++ enabledSelect)
      .sort(sortPopular)
      .cursor[Team](ReadPreference.secondaryPreferred)
      .list(100)

  def enabledTeamIdsByLeader(userId: User.ID): Fu[List[Team.ID]] =
    coll
      .primitive[Team.ID](
        $doc("leaders" -> userId) ++ enabledSelect,
        sortPopular,
        "_id"
      )

  def leadersOf(teamId: Team.ID): Fu[Set[User.ID]] =
    coll.primitiveOne[Set[User.ID]]($id(teamId), "leaders").dmap(~_)

  def setLeaders(teamId: Team.ID, leaders: Set[User.ID]): Funit =
    coll.updateField($id(teamId), "leaders", leaders).void

  def leads(teamId: Team.ID, userId: User.ID) =
    coll.exists($id(teamId) ++ $doc("leaders" -> userId))

  def name(id: Team.ID): Fu[Option[String]] =
    coll.primitiveOne[String]($id(id), "name")

  def mini(id: Team.ID): Fu[Option[Team.Mini]] =
    name(id) map2 { Team.Mini(id, _) }

  private[team] def countCreatedSince(userId: String, duration: Period): Fu[Int] =
    coll.countSel(
      $doc(
        "createdAt" $gt DateTime.now.minus(duration),
        "createdBy" -> userId
      )
    )

  def incMembers(teamId: Team.ID, by: Int): Funit =
    coll.update.one($id(teamId), $inc("nbMembers" -> by)).void

  def enable(team: Team): Funit =
    coll.updateField($id(team.id), "enabled", true).void

  def disable(team: Team): Funit =
    coll.updateField($id(team.id), "enabled", false).void

  def addRequest(teamId: Team.ID, request: Request): Funit =
    coll.update
      .one(
        $id(teamId) ++ $doc("requests.user" $ne request.user),
        $push("requests" -> request.user)
      )
      .void

  def cursor =
    coll
      .find(enabledSelect)
      .cursor[Team](ReadPreference.secondaryPreferred)

  def countRequestsOfLeader(userId: User.ID, requestColl: Coll): Fu[Int] =
    coll
      .aggregateOne(readPreference = ReadPreference.secondaryPreferred) { implicit framework =>
        import framework._
        Match($doc("leaders" -> userId)) -> List(
          Group(BSONNull)("ids" -> PushField("_id")),
          PipelineOperator(
            $doc(
              "$lookup" -> $doc(
                "from" -> requestColl.name,
                "as"   -> "requests",
                "let"  -> $doc("teams" -> "$ids"),
                "pipeline" -> $arr(
                  $doc(
                    "$match" -> $doc(
                      "$expr" -> $doc(
                        "$and" -> $arr(
                          $doc("$in" -> $arr("$team", "$$teams")),
                          $doc("$ne" -> $arr("$declined", true))
                        )
                      )
                    )
                  )
                )
              )
            )
          ),
          Group(BSONNull)(
            "nb" -> Sum($doc("$size" -> "$requests"))
          )
        )
      }
      .map(~_.flatMap(_.int("nb")))

  def forumAccess(id: Team.ID): Fu[Option[Team.Access]] =
    coll.secondaryPreferred.primitiveOne[Team.Access]($id(id), "forum")

  def filterHideMembers(ids: Iterable[Team.ID]): Fu[Set[Team.ID]] =
    coll.secondaryPreferred.distinctEasy[Team.ID, Set]("_id", $inIds(ids) ++ $doc("hideMembers" -> true))

  private[team] val enabledSelect = $doc("enabled" -> true)

  private[team] val sortPopular = $sort desc "nbMembers"
}
