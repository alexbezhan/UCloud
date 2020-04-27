package dk.sdu.cloud.avatar.services

import dk.sdu.cloud.avatar.api.*
import dk.sdu.cloud.service.db.HibernateEntity
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.WithId
import dk.sdu.cloud.service.db.criteria
import dk.sdu.cloud.service.db.get
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "avatars")
class AvatarEntity(
    @Column
    @Id
    var username: String,

    @Enumerated(EnumType.STRING)
    var top: Top,

    @Enumerated(EnumType.STRING)
    var topAccessory: TopAccessory,

    @Enumerated(EnumType.STRING)
    var hairColor: HairColor,

    @Enumerated(EnumType.STRING)
    var facialHair: FacialHair,

    @Enumerated(EnumType.STRING)
    var facialHairColor: FacialHairColor,

    @Enumerated(EnumType.STRING)
    var clothes: Clothes,

    @Enumerated(EnumType.STRING)
    var colorFabric: ColorFabric,

    @Enumerated(EnumType.STRING)
    var eyes: Eyes,

    @Enumerated(EnumType.STRING)
    var eyebrows: Eyebrows,

    @Enumerated(EnumType.STRING)
    var mouthTypes: MouthTypes,

    @Enumerated(EnumType.STRING)
    var skinColors: SkinColors,

    @Enumerated(EnumType.STRING)
    var clothesGraphic: ClothesGraphic,

    @Enumerated(EnumType.STRING)
    var hatColor: HatColor
) {
    companion object : HibernateEntity<AvatarEntity>, WithId<String>
}

private fun defaultAvatar(): Avatar =
    Avatar(
        Top.NO_HAIR,
        TopAccessory.BLANK,
        HairColor.BLACK,
        FacialHair.BLANK,
        FacialHairColor.BLACK,
        Clothes.SHIRT_CREW_NECK,
        ColorFabric.BLACK,
        Eyes.DEFAULT,
        Eyebrows.DEFAULT,
        MouthTypes.SMILE,
        SkinColors.LIGHT,
        ClothesGraphic.BEAR,
        HatColor.BLUE01
    )


fun AvatarEntity.toModel(): Avatar = Avatar(
    top,
    topAccessory,
    hairColor,
    facialHair,
    facialHairColor,
    clothes,
    colorFabric,
    eyes,
    eyebrows,
    mouthTypes,
    skinColors,
    clothesGraphic,
    hatColor
)

fun Avatar.toEntity(user: String): AvatarEntity = AvatarEntity(
    user,
    top,
    topAccessory,
    hairColor,
    facialHair,
    facialHairColor,
    clothes,
    colorFabric,
    eyes,
    eyebrows,
    mouthTypes,
    skinColors,
    clothesGraphic,
    hatColor
)

class AvatarHibernateDAO : AvatarDAO<HibernateSession> {

    override fun upsert(
        session: HibernateSession,
        user: String,
        avatar: Avatar
    ) {
        val foundAvatar = findInternal(session, user)
        if (foundAvatar != null) {
            foundAvatar.top = avatar.top
            foundAvatar.topAccessory = avatar.topAccessory
            foundAvatar.hairColor = avatar.hairColor
            foundAvatar.facialHair = avatar.facialHair
            foundAvatar.facialHairColor = avatar.facialHairColor
            foundAvatar.clothes = avatar.clothes
            foundAvatar.colorFabric = avatar.colorFabric
            foundAvatar.eyes = avatar.eyes
            foundAvatar.eyebrows = avatar.eyebrows
            foundAvatar.mouthTypes = avatar.mouthTypes
            foundAvatar.skinColors = avatar.skinColors
            foundAvatar.clothesGraphic = avatar.clothesGraphic
            foundAvatar.hatColor = avatar.hatColor
            session.update(foundAvatar)
        } else {
            val entity = avatar.toEntity(user)
            session.save(entity)
        }
    }

    private fun findInternal(
        session: HibernateSession,
        user: String
    ): AvatarEntity? {
        return session.criteria<AvatarEntity> {
            (entity[AvatarEntity::username] equal user)
        }.uniqueResult()
    }

    override fun findByUser(
        session: HibernateSession,
        user: String
    ): Avatar {
        return session.criteria<AvatarEntity> {
            (entity[AvatarEntity::username] equal user)
        }.uniqueResult()?.toModel() ?: defaultAvatar()
    }

    override fun bulkFind(
        session: HibernateSession,
        users: List<String>
    ): Map<String, SerializedAvatar> {
        return users.map { username ->
            val avatar = findByUser(session, username)
            username to SerializedAvatar(
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
        }.toMap()
    }
}
