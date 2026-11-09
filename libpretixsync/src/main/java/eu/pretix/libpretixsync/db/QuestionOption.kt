package eu.pretix.libpretixsync.db


class QuestionOption {
    var server_id: Long? = null
    var position: Long? = null
    var identifier: String? = null
    var value: String? = null

    constructor(server_id: Long?, position: Long?, identifier: String?, value: String?) {
        this.server_id = server_id
        this.position = position
        this.identifier = identifier
        this.value = value
    }

    constructor()


    override fun toString(): String {
        return value!!
    }
}
