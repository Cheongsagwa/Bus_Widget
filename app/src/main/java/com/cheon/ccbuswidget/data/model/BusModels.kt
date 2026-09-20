package com.cheon.ccbuswidget.data.model

/**
 * 차종 DB.
 *
 * 수정 방법
 *  1) 차종 이름을 바꾸고 싶으면 아래 "차종 목록" 의 Model(...) 안의 글자만 고치면
 *     그 차종을 쓰는 모든 차량에 한 번에 반영된다.
 *     Model(전체이름, 짧은이름) — 짧은이름이 노선 타임라인의 차량번호 아래 칩에 표시된다.
 *  2) 차량을 추가하거나 고치려면 아래 "차량 목록" 에
 *     Vehicle(차량번호4자리, 차량연식, 차종상수, 부가정보) 한 줄을 넣으면 된다.
 *     부가정보는 생략 가능.
 */
object BusModels {

    data class Model(
        /** 전체 차종명 (예: "일렉시티Ⅱ EV") */
        val name: String,
        /** 칩에 표시할 짧은 차종명 (예: "ElecCity Ⅱ") */
        val short: String
    )

    data class Vehicle(
        /** 차량번호 뒤 4자리 */
        val no: String,
        /** 차량연식 (예: "25.10") */
        val year: String,
        /** 차종 */
        val model: Model,
        /** 면허등록일자 등 부가정보 */
        val note: String = ""
    )

    // ─────────────────────────── 차종 목록 ───────────────────────────
    // 여기 이름만 고치면 아래 차량 목록 전체에 반영된다.

    val ELECCITY2_EV      = Model("일렉시티Ⅱ EV", "ElecCity Ⅱ")
    val ELECCITY2_FCEV    = Model("일렉시티Ⅱ FCEV", "ElecCity FC")
    val NEW_COUNTY_DIESEL = Model("뉴카운티 디젤", "New County")
    val COUNTY_NEW_BRZ    = Model("카운티 뉴 브리즈 디젤 앨리슨 오토", "County NB")
    val CRRC_C1100_EV     = Model("CRRC 그린웨이1100 EV", "CRRC 1100")
    val CRRC_C1100_TC_EV  = Model("CRRC 그린웨이1100 타이거클래식트롤리버스 EV", "CRRC Trolly")
    val NSAC_LF_CNG       = Model("저상 뉴슈퍼에어로시티 F/L CNG", "NSAC LF")
    val E_FIBIRD_EV       = Model("뉴 E-화이버드 EV", "E-Fibird")
    val GREENCITY_CNG     = Model("그린시티 CNG 개선형", "Green City")
    val GREENCITY_DIESEL  = Model("그린시티 디젤 개선형", "Green City")
    val HIGER_HYPERSE_EV  = Model("하이거 하이퍼스 EV", "HYPERSE")
    val SMART_110_EV      = Model("스마트 110 EV", "SMART 110")
    val SKYWELL_HUSKY_EV  = Model("스카이웰 HU-SKY EV", "HU-SKY")

    /** 전체 차종 목록 */
    val allModels: List<Model> = listOf(
        ELECCITY2_EV,
        ELECCITY2_FCEV,
        NEW_COUNTY_DIESEL,
        COUNTY_NEW_BRZ,
        CRRC_C1100_EV,
        CRRC_C1100_TC_EV,
        NSAC_LF_CNG,
        E_FIBIRD_EV,
        GREENCITY_CNG,
        GREENCITY_DIESEL,
        HIGER_HYPERSE_EV,
        SMART_110_EV,
        SKYWELL_HUSKY_EV,
    )

    // ─────────────────────────── 차량 목록 ───────────────────────────
    // Vehicle(차량번호, 연식, 차종, 부가정보)

    private val vehicleList: List<Vehicle> = listOf(
        Vehicle("1009", "25.10", ELECCITY2_EV,      "2025.12 면허등록"),
        Vehicle("1012", "25.10", ELECCITY2_EV,      "2025.12 면허등록"),
        Vehicle("1013", "25.10", ELECCITY2_EV,      "2025.12 면허등록"),
        Vehicle("1016", "23.9",  ELECCITY2_EV,      "2023.10 면허등록"),
        Vehicle("1018", "23.9",  ELECCITY2_EV,      "2023.10 면허등록"),
        Vehicle("1019", "24.6",  ELECCITY2_EV,      "(2024.7 면허등록) (300번, 904번 공용차량)"),
        Vehicle("1020", "24.7",  ELECCITY2_EV,      "[패찰연식 2024.6]"),
        Vehicle("1021", "23.5",  ELECCITY2_EV,      "2023.6 면허등록, 구.강원 70자 1223호"),
        Vehicle("1022", "24.6",  ELECCITY2_EV,      "2024.7 면허등록"),
        Vehicle("1023", "20.10", E_FIBIRD_EV,       "강원특별자치도 최초의 E-화이버드"),
        Vehicle("1027", "24.4",  ELECCITY2_EV,      "2024.5 면허등록"),
        Vehicle("1028", "24.11", ELECCITY2_EV,      "2024.12 면허등록"),
        Vehicle("1029", "24.11", ELECCITY2_EV,      "2024.12 면허등록[패찰연식 2024.10]"),
        Vehicle("1031", "24.11", ELECCITY2_EV,      "2024.12 면허등록"),
        Vehicle("1034", "24.4",  ELECCITY2_EV,      "(2024.5 면허등록) (9번, 905번 공용차량 겸 200번 오전지원 운행차량)"),
        Vehicle("1035", "24.4",  ELECCITY2_EV,      "(2024.5 면허등록) (17번, 903번 공용차량 겸 300번 오전 1회지원 운행차량)"),
        Vehicle("1040", "20.11", E_FIBIRD_EV,       "2020.12 면허등록"),
        Vehicle("1041", "19.4",  SKYWELL_HUSKY_EV,  "2020.11 면허등록"),
        Vehicle("1042", "22.12", CRRC_C1100_TC_EV,  "2023.12 면허등록"),
        Vehicle("1045", "19.12", E_FIBIRD_EV,       "2021.12 면허등록"),
        Vehicle("1050", "24.11", ELECCITY2_EV,      "2024.12 면허등록"),
        Vehicle("1051", "25.10", ELECCITY2_EV,      "2025.12 면허등록"),
        Vehicle("1052", "25.10", ELECCITY2_EV,      "2025.12 면허등록"),
        Vehicle("1053", "16.5",  NSAC_LF_CNG,       "구.대동운수 출신"),
        Vehicle("1057", "20.12", CRRC_C1100_EV,     "(2021.5 면허등록) 전국 최초의 CRRC 그린웨이1100)"),
        Vehicle("1058", "24.4",  ELECCITY2_EV,      "2024.5 면허등록"),
        Vehicle("1059", "24.7",  ELECCITY2_EV,      "[패찰연식 2024.6]"),
        Vehicle("1061", "20.12", HIGER_HYPERSE_EV,  "강원특별자치도 최초의 하이거 하이퍼스"),
        Vehicle("1062", "23.9",  ELECCITY2_EV,      "2023.10 면허등록"),
        Vehicle("1063", "25.2",  ELECCITY2_FCEV,    "2025.11 면허등록"),
        Vehicle("1064", "25.10", ELECCITY2_EV,      "2025.12 면허등록"),
        Vehicle("1066", "24.6",  ELECCITY2_EV,      "2024.7 면허등록"),
        Vehicle("1067", "24.4",  ELECCITY2_EV,      "(2024.5 면허등록) (300번, 906번 공용차량 겸 200-1번 오전지원 운행차량)"),
        Vehicle("1068", "23.9",  ELECCITY2_EV,      "2023.10 면허등록"),
        Vehicle("1069", "24.7",  ELECCITY2_EV,      "[패찰연식 2024.6]"),
        Vehicle("1070", "24.4",  ELECCITY2_EV,      "2024.5 면허등록"),
        Vehicle("1076", "24.6",  ELECCITY2_EV,      "2024.7 면허등록"),
        Vehicle("1077", "23.9",  ELECCITY2_EV,      "2023.10 면허등록"),
        Vehicle("1078", "20.12", HIGER_HYPERSE_EV,  "강원특별자치도 최초의 하이거 하이퍼스"),
        Vehicle("1080", "25.7",  ELECCITY2_FCEV,    "2025.11 면허등록"),
        Vehicle("1085", "21.12", CRRC_C1100_EV),
        Vehicle("1086", "21.12", CRRC_C1100_EV),
        Vehicle("1087", "22.5",  SMART_110_EV,      "강원특별자치도 최초의 스마트 110"),
        Vehicle("1088", "23.4",  ELECCITY2_EV,      "강원특별자치도 최초의 일렉시티"),
        Vehicle("1089", "23.4",  ELECCITY2_EV,      "강원특별자치도 최초의 일렉시티"),
        Vehicle("1090", "23.4",  ELECCITY2_EV,      "강원특별자치도 최초의 일렉시티"),
        Vehicle("1091", "23.4",  ELECCITY2_EV,      "2023.5 면허등록"),
        Vehicle("1092", "23.5",  ELECCITY2_EV,      "2023.6 면허등록"),
        Vehicle("1093", "24.3",  ELECCITY2_EV),
        Vehicle("1094", "24.6",  ELECCITY2_EV,      "[패찰연식 2024.5]"),
        Vehicle("1095", "24.6",  ELECCITY2_EV,      "[패찰연식 2024.5]"),
        Vehicle("1096", "23.9",  ELECCITY2_EV,      "2023.10 면허등록"),
        Vehicle("1097", "23.9",  ELECCITY2_EV,      "2023.10 면허등록"),
        Vehicle("1098", "24.7",  ELECCITY2_EV,      "2024.8 면허등록[패찰연식 2024.6]"),
        Vehicle("1099", "24.7",  ELECCITY2_EV,      "(2024.8 면허등록[패찰연식 2024.6]) (200번, 200-1번, 901번 공용차량 겸 17번 오전지원 운행차량)"),
        Vehicle("1202", "25.4",  ELECCITY2_FCEV,    "2025.8 면허등록"),
        Vehicle("1203", "25.10", ELECCITY2_EV,      "2025.12 면허등록"),
        Vehicle("1207", "24.5",  ELECCITY2_EV,      "[패찰연식 2024.4]"),
        Vehicle("1209", "17.7",  NSAC_LF_CNG,       "구.대한운수 출신"),
        Vehicle("1210", "25.4",  ELECCITY2_FCEV,    "2025.8 면허등록"),
        Vehicle("1212", "17.5",  NSAC_LF_CNG,       "2017.6 면허등록, 구.대한운수 출신"),
        Vehicle("1213", "23.5",  ELECCITY2_EV,      "2023.6 면허등록, 구.강원 70자 1285호"),
        Vehicle("1214", "25.10", ELECCITY2_EV,      "2025.12 면허등록"),
        Vehicle("1217", "24.6",  ELECCITY2_EV,      "[패찰연식 2024.5]"),
        Vehicle("1218", "17.6",  NSAC_LF_CNG,       "구.대한운수 출신"),
        Vehicle("1223", "23.5",  ELECCITY2_EV,      "2023.6 면허등록, 구.강원 70자 1213호"),
        Vehicle("1227", "20.11", E_FIBIRD_EV,       "2020.12 면허등록"),
        Vehicle("1230", "20.10", E_FIBIRD_EV,       "강원특별자치도 최초의 E-화이버드"),
        Vehicle("1231", "19.4",  SKYWELL_HUSKY_EV,  "2020.11 면허등록"),
        Vehicle("1233", "24.11", ELECCITY2_EV,      "(2024.12 면허등록) (100번/100-1번, 902번 공용차량)"),
        Vehicle("1238", "25.10", ELECCITY2_FCEV,    "2025.12 면허등록"),
        Vehicle("1241", "16.5",  NSAC_LF_CNG,       "2016.6 면허등록, 구.대한운수 출신"),
        Vehicle("1242", "24.11", ELECCITY2_EV,      "2024.12 면허등록"),
        Vehicle("1243", "17.5",  NSAC_LF_CNG,       "2017.6 면허등록, 구.대한운수 출신"),
        Vehicle("1245", "25.10", ELECCITY2_EV,      "2025.12 면허등록"),
        Vehicle("1250", "23.9",  ELECCITY2_EV,      "2023.10 면허등록"),
        Vehicle("1252", "23.1",  CRRC_C1100_TC_EV,  "2023.12 면허등록[패찰연식 2022.12]"),
        Vehicle("1253", "25.7",  ELECCITY2_FCEV,    "2025.12 면허등록"),
        Vehicle("1254", "25.10", ELECCITY2_EV,      "2025.12 면허등록"),
        Vehicle("1255", "22.12", CRRC_C1100_TC_EV,  "2023.12 면허등록"),
        Vehicle("1256", "25.4",  ELECCITY2_FCEV,    "2025.8 면허등록"),
        Vehicle("1257", "24.11", ELECCITY2_EV,      "2024.12 면허등록"),
        Vehicle("1259", "20.12", HIGER_HYPERSE_EV,  "강원특별자치도 최초의 하이거 하이퍼스"),
        Vehicle("1261", "25.10", ELECCITY2_EV,      "2025.12 면허등록"),
        Vehicle("1263", "26.1",  ELECCITY2_FCEV,    "(2026.2 면허등록) (강원특별자치도 시내/농어촌/마을버스 최초의 2026년식 차량)"),
        Vehicle("1265", "20.12", HIGER_HYPERSE_EV,  "강원특별자치도 최초의 하이거 하이퍼스"),
        Vehicle("1266", "25.8",  ELECCITY2_FCEV,    "2025.12 면허등록[패찰연식 2025.7]"),
        Vehicle("1268", "25.11", ELECCITY2_EV,      "2025.12 면허등록"),
        Vehicle("1269", "24.6",  ELECCITY2_EV,      "2024.7 면허등록"),
        Vehicle("1270", "24.3",  ELECCITY2_EV),
        Vehicle("1271", "24.3",  ELECCITY2_EV),
        Vehicle("1272", "17.7",  NSAC_LF_CNG,       "구.대한운수 출신"),
        Vehicle("1273", "25.8",  ELECCITY2_FCEV,    "2025.11 면허등록"),
        Vehicle("1278", "20.12", CRRC_C1100_EV,     "(2021.5 면허등록) (전국 최초의 CRRC 그린웨이1100)"),
        Vehicle("1279", "20.12", CRRC_C1100_EV,     "(2021.5 면허등록) (전국 최초의 CRRC 그린웨이1100)"),
        Vehicle("1280", "22.5",  SMART_110_EV,      "강원특별자치도 최초의 스마트 110"),
        Vehicle("1281", "23.4",  ELECCITY2_EV,      "강원특별자치도 최초의 일렉시티"),
        Vehicle("1282", "22.5",  SMART_110_EV,      "강원특별자치도 최초의 스마트 110"),
        Vehicle("1283", "23.5",  ELECCITY2_EV,      "2023.6 면허등록"),
        Vehicle("1284", "23.5",  ELECCITY2_EV,      "2023.6 면허등록"),
        Vehicle("1285", "23.5",  ELECCITY2_EV,      "2023.6 면허등록, 구.강원 70자 1021호"),
        Vehicle("1286", "24.11", ELECCITY2_EV,      "(2024.12 면허등록[패찰연식 2024.10]) (13-1번/101번 공용차량, 12번 편도차출차량으로도 운행)"),
        Vehicle("1287", "23.9",  ELECCITY2_EV,      "2023.10 면허등록"),
        Vehicle("1288", "24.7",  ELECCITY2_EV,      "2024.8 면허등록[패찰연식 2024.6]"),
        Vehicle("1700", "19.10", NEW_COUNTY_DIESEL, "춘천시민버스 출신"),
        Vehicle("1701", "19.10", NEW_COUNTY_DIESEL, "춘천시민버스 출신"),
        Vehicle("1702", "19.9",  NEW_COUNTY_DIESEL, "2019.10 면허등록, 춘천시민버스 출신"),
        Vehicle("1703", "19.10", NEW_COUNTY_DIESEL, "춘천시민버스 출신"),
        Vehicle("1704", "19.10", NEW_COUNTY_DIESEL, "춘천시민버스 출신"),
        Vehicle("1705", "19.10", NEW_COUNTY_DIESEL, "춘천시민버스 출신"),
        Vehicle("1706", "19.10", NEW_COUNTY_DIESEL, "춘천시민버스 출신"),
        Vehicle("1707", "19.10", NEW_COUNTY_DIESEL, "춘천시민버스 출신"),
        Vehicle("1708", "19.10", NEW_COUNTY_DIESEL, "춘천시민버스 출신"),
        Vehicle("1709", "19.10", NEW_COUNTY_DIESEL, "춘천시민버스 출신"),
        Vehicle("1710", "19.10", NEW_COUNTY_DIESEL, "춘천시민버스 출신"),
        Vehicle("1711", "19.10", NEW_COUNTY_DIESEL, "춘천시민버스 출신"),
        Vehicle("1712", "19.10", NEW_COUNTY_DIESEL, "춘천시민버스 출신"),
        Vehicle("1713", "19.10", NEW_COUNTY_DIESEL, "춘천시민버스 출신"),
        Vehicle("1714", "19.10", NEW_COUNTY_DIESEL, "춘천시민버스 출신"),
        Vehicle("1715", "19.10", NEW_COUNTY_DIESEL, "춘천시민버스 출신"),
        Vehicle("1716", "19.10", NEW_COUNTY_DIESEL, "춘천시민버스 출신"),
        Vehicle("1717", "19.10", NEW_COUNTY_DIESEL, "춘천시민버스 출신"),
        Vehicle("1718", "19.10", NEW_COUNTY_DIESEL, "춘천시민버스 출신"),
        Vehicle("1719", "19.10", NEW_COUNTY_DIESEL, "춘천시민버스 출신"),
        Vehicle("1720", "19.10", NEW_COUNTY_DIESEL, "춘천시민버스 출신"),
        Vehicle("1721", "19.10", NEW_COUNTY_DIESEL, "춘천시민버스 출신"),
        Vehicle("1722", "20.7",  CRRC_C1100_EV,     "2021.10 면허등록"),
        Vehicle("1723", "20.12", CRRC_C1100_EV,     "2021.10 면허등록"),
        Vehicle("1724", "20.12", CRRC_C1100_EV,     "2021.10 면허등록"),
        Vehicle("1725", "20.12", CRRC_C1100_EV,     "2021.10 면허등록"),
        Vehicle("1726", "20.12", CRRC_C1100_EV,     "2021.10 면허등록"),
        Vehicle("1728", "23.6",  COUNTY_NEW_BRZ),
        Vehicle("1729", "23.6",  COUNTY_NEW_BRZ),
        Vehicle("1730", "19.9",  NEW_COUNTY_DIESEL, "2019.10 면허등록"),
        Vehicle("1731", "19.9",  NEW_COUNTY_DIESEL, "2019.10 면허등록"),
        Vehicle("1732", "19.9",  NEW_COUNTY_DIESEL, "2019.10 면허등록"),
        Vehicle("1733", "19.9",  NEW_COUNTY_DIESEL, "2019.10 면허등록"),
        Vehicle("1734", "19.9",  NEW_COUNTY_DIESEL, "2019.10 면허등록"),
        Vehicle("1735", "19.9",  NEW_COUNTY_DIESEL, "2019.10 면허등록"),
        Vehicle("1736", "19.9",  NEW_COUNTY_DIESEL, "2019.10 면허등록"),
        Vehicle("1737", "19.10", NEW_COUNTY_DIESEL),
        Vehicle("1738", "20.12", GREENCITY_CNG,     "2021.1 면허등록"),
        Vehicle("1739", "21.2",  GREENCITY_CNG,     "2021.3 면허등록, 춘천시민버스 출신"),
        Vehicle("1740", "21.3",  GREENCITY_CNG,     "패찰에는 2021년 2월식으로 적힘, 춘천시민버스 출신"),
        Vehicle("1741", "21.3",  GREENCITY_CNG,     "패찰에는 2021년 2월식으로 적힘, 춘천시민버스 출신"),
        Vehicle("1742", "21.3",  GREENCITY_CNG,     "2021.4 면허등록, 춘천시민버스 출신"),
        Vehicle("1743", "23.5",  GREENCITY_DIESEL,  "2023.6 면허등록"),
        Vehicle("1745", "23.5",  GREENCITY_DIESEL,  "2023.6 면허등록"),
    )

    private val byNumber: Map<String, Vehicle> = vehicleList.associateBy { it.no }

    /** 전체 차량 목록 (읽기 전용) */
    val vehicles: List<Vehicle> get() = vehicleList

    /** "강원70자1077" 이든 "1077" 이든 숫자 뒤 4자리로 조회한다. */
    fun find(vehicleNo: String?): Vehicle? {
        if (vehicleNo.isNullOrBlank()) return null
        val digits = vehicleNo.filter { it.isDigit() }
        if (digits.length < 4) return null
        return byNumber[digits.takeLast(4)]
    }

    /** 칩에 표시할 짧은 차종명. 등록되지 않은 차량이면 null. */
    fun shortName(vehicleNo: String?): String? = find(vehicleNo)?.model?.short

    /** 전체 차종명. 등록되지 않은 차량이면 null. */
    fun fullName(vehicleNo: String?): String? = find(vehicleNo)?.model?.name
}

