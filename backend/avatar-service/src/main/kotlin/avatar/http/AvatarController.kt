package dk.sdu.cloud.avatar.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.avatar.api.*
import dk.sdu.cloud.avatar.services.AvatarService
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import io.ktor.http.HttpStatusCode

class AvatarController(
    private val avatarService: AvatarService
) : Controller {
    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {

        implement(AvatarDescriptions.update) {
            val user = ctx.securityPrincipal.username
            val avatar = approvedAvatar(
                request.top,
                request.topAccessory,
                request.hairColor,
                request.facialHair,
                request.facialHairColor,
                request.clothes,
                request.colorFabric,
                request.eyes,
                request.eyebrows,
                request.mouthTypes,
                request.skinColors,
                request.clothesGraphic,
                request.hatColor
            )
            if (avatar != null) {
                avatarService.upsert(user, avatar)
                ok(Unit)
            } else {
                error(CommonErrorMessage("Bad request"), HttpStatusCode.BadRequest)
            }
        }

        implement(AvatarDescriptions.findAvatar) {
            val avatar = avatarService.findByUser(ctx.securityPrincipal.username)
            ok(
                FindResponse(
                    avatar.top.string,
                    avatar.topAccessory.string,
                    avatar.hairColor.string,
                    avatar.facialHair.string,
                    avatar.facialHairColor.string,
                    avatar.clothes.string,
                    avatar.colorFabric.string,
                    avatar.eyes.string,
                    avatar.eyebrows.string,
                    avatar.mouthTypes.string,
                    avatar.skinColors.string,
                    avatar.clothesGraphic.string,
                    avatar.hatColor.string
                )
            )
        }

        implement(AvatarDescriptions.findBulk) {
            ok(FindBulkResponse(avatarService.bulkFind(request.usernames)))
        }
    }

    private fun approvedAvatar(
        top: String,
        topAccessory: String,
        hairColor: String,
        facialHair: String,
        facialHairColor: String,
        clothes: String,
        colorFabric: String,
        eyes: String,
        eyebrows: String,
        mouthTypes: String,
        skinColors: String,
        clothesGraphic: String,
        hatColor: String
    ): Avatar? {
        return try {
            Avatar(
                Top.fromString(top),
                TopAccessory.fromString(topAccessory),
                HairColor.fromString(hairColor),
                FacialHair.fromString(facialHair),
                FacialHairColor.fromString(facialHairColor),
                Clothes.fromString(clothes),
                ColorFabric.fromString(colorFabric),
                Eyes.fromString(eyes),
                Eyebrows.fromString(eyebrows),
                MouthTypes.fromString(mouthTypes),
                SkinColors.fromString(skinColors),
                ClothesGraphic.fromString(clothesGraphic),
                HatColor.fromString(hatColor)
            )
        } catch (e: IllegalArgumentException) {
            return null
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
