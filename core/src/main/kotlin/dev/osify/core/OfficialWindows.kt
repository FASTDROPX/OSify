package dev.osify.core

/** Public 90-day evaluation from Microsoft's Evaluation Center; no redistribution. */
object OfficialWindows {
    const val HOST="software-static.download.prss.microsoft.com"
    val windows11=CatalogImage("windows-11-eval","Windows 11","Enterprise LTSC 2024 Evaluation (EN)",Architecture.X86_64,"windows-11-eval",
        "https://software-static.download.prss.microsoft.com/dbazure/888969d5-f34g-4e03-ac9d-1f9786c66749/26100.1742.240906-0331.ge_release_svc_refresh_CLIENT_LTSC_EVAL_x64FRE_en-us.iso",
        5112850432L,"",license="Microsoft evaluation terms, 90 days",source="https://www.microsoft.com/en-us/evalcenter/download-windows-11-enterprise")
    val windows10=CatalogImage("windows-10-source","Windows 10","Enterprise LTSC 2021 Evaluation (EN)",Architecture.X86_64,"windows-10-source",
        "https://software-static.download.prss.microsoft.com/db/download/444969d5-f34g-4e03-ac9d-1f9786c69161/19044.1288.211006-0501.21h2_release_svc_refresh_CLIENT_LTSC_EVAL_x64FRE_en-us.iso",
        4898582528L,"",license="Microsoft evaluation terms, 90 days",source="https://www.microsoft.com/en-us/evalcenter/download-windows-10-enterprise")
    fun isEvaluation(image: CatalogImage)=listOf(windows11,windows10).any {image.flavor==it.flavor && image.url==it.url && image.bytes==it.bytes && image.architecture==Architecture.X86_64}
}
