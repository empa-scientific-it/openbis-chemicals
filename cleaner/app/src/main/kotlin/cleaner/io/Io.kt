package cleaner.io

import cleaner.chemicals.CAS
import cleaner.openbis.OpenbisPropertyMapping
import com.actelion.research.chem.io.SDFileParser
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.io.readTSV
import org.jetbrains.kotlinx.dataframe.name
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.nio.charset.StandardCharsets


import cleaner.io.Chemical
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.kotlinx.dataframe.api.*
import org.jetbrains.kotlinx.dataframe.impl.asList
import java.lang.reflect.Field
import javax.management.monitor.StringMonitor

//@OptIn(ExperimentalSerializationApi::class)
//fun getMoleculesFromOpenbis(
//    url: String,
//    username: String,
//    password: String,
//    collection: String,
//    mapping: String
//): List<SourceMolecule> {
//    val openbis = createOpenbisInstance(URI(url))
//    val token = openbis.login(username, password)
//    val coll = getObjectsInCollection(token, openbis, collection)
//    val objectConfig = Json.decodeFromString<OpenbisPropertyMapping>(File(mapping).readText())
//    return coll.map { it -> SourceMolecule(objectConfig.mapSamples(it)) }
//}

fun <K> getValuesFromFile(file: File, mapping: OpenbisPropertyMapping): DataFrame<K> {
    val nameMapping = mapping.fields
    val df = DataFrame.readTSV(file, charset = StandardCharsets.UTF_16)
    val transformed = mapping.transformMap(df.toMap())
    val mappedDf = mapping.transformMap(df.toMap()).toDataFrame().cast<K>()
    return mappedDf

}

fun SDFileParser.getFieldByName(name: String): String? {
    val idx = this.getFieldIndex(name)
    val res = if (idx >= 0) {
        val dt = this.getFieldData(idx)
        dt
    } else {
        null
    }
    return res
}


fun SDFileParser.getFieldsByName(names: List<String>): Map<String, String?> {
    return names.associateWith { this.getFieldByName(it) }
}


operator fun SDFileParser.iterator(): Iterator<SDFileParser> {
    return object : Iterator<SDFileParser> {
        val cur = 0;
        override fun hasNext(): Boolean = this@iterator.next()
        override fun next(): SDFileParser {
            this@iterator.next();
            return this@iterator
        }
    }
}

/***
 * Iterate over a SDF file in batches
 * and get the chosen fields
 */
fun SDFileParser.batchedIterator(fields: List<String>, batch: Int = 100): Iterator<List<Map<String, String?>>> {
    return object : Iterator<List<Map<String, String?>>> {
        override fun next(): List<Map<String, String?>> {
            val res = listOf<Map<String, String?>>().toMutableList()
            (1..batch).forEach {
                if (this@batchedIterator.next()) {
                    val values = this@batchedIterator.getFieldsByName(fields)
                    res.add(values)
                }
            }
            return res.toList()
        }

        override fun hasNext(): Boolean = this@batchedIterator.next()

    }
}

/**
 * Safely opens a SDF File
 */
fun openSDF(name: String, fn: List<String>?): SDFileParser {
    var fileParser = SDFileParser(name)
    val fnOut = fn?.toTypedArray() ?: fileParser.fieldNames
    fileParser.close()
    return SDFileParser(name, fnOut)
}


fun <R> SDFileParser.use(code: ((SDFileParser) -> R)): R {
    val res = code(this)
    this.close()
    return res
}


val ChEBIFields =
    mapOf(
        "ChEBI ID" to "ID",
        "CAS Registry Numbers" to "cas",
        "ChEBI Name" to "chEBIName",
        "InChIKey" to "inchiKey",
        "InChI" to "inchi",
        "IUPAC Names" to "iupacName",
        "Formulae" to "formula"
    )
private val NCIFields = mapOf<String, String>(
    "CAS" to "cas",
    "Standard InChi" to "inchi",
    "Standard InChIKey" to "inchiKey",
    "DTP names" to "name",
    "Formula" to "formula"
)


class FieldMapping(
    val id: String,
    val cas: String,
    val inchi: String,
    val inchiKey: String,
    val smiles: String?,
    val iupacName: String,
    val formula: String
){
    fun getSDFAttibutes(): List<String> {
        val ls = listOf(id, cas, inchi, inchiKey, formula, iupacName)
        return if(smiles != null) {
            smiles?.let{ls + it} !!
        }else{
            ls
        }
    }

}

enum class ChemicalsSource(val fields: FieldMapping) {

    CHEBI(
        FieldMapping(
            "ChEBI ID",
            "CAS Registry Numbers",
            "InChI",
            "InChIKey",
            "SMILES",
            "ChEBI Name",
            "Formulae"
        )
    ),
    NCI(
        FieldMapping(
            "NCICADD_FICUS_ID",
            "CAS",
            "Standard InChi",
            "Standard InChIKey",
            null,
            "DTP names",
            "Formula"
        )
    )
}

/***
 * Adapter class representing a
 * generic database entry for chemicals
 */
data class ChemicalsDBEntry(private val vals: Map<String, String?>, private val mapping: ChemicalsSource) {
    val id: String = vals[mapping.fields.id]!!
    val CAS: String = vals[mapping.fields.cas]!!
    val inchiKey: String = vals[mapping.fields.inchiKey]!!
    val inchi: String  = vals[mapping.fields.inchi]!!
    val smiles: String? = vals[mapping.fields?.smiles]
    val name: String = vals[mapping.fields.iupacName]?.split("\n")?.get(0) ?: vals[mapping.fields.iupacName]!!
    val formula: String = vals[mapping.fields.formula]!!
}



/**
 * Iterates over a SDF file and stores the data
 * in a SQLLIte database
 */
fun sdfToSQL(sdfPath: String, db: Database, fileType: ChemicalsSource, batchSize: Int = 1000): Unit {
    val logger = LoggerFactory.getLogger("SQL")
    val fields = fileType.fields.getSDFAttibutes()
    openSDF(sdfPath, fields).use { sdf ->
        logger.info("Selected fields ${fields}, Available fields ${sdf.fieldNames.toList()}")
        transaction(db) {
            for ((index, batch) in sdf.batchedIterator(fields, batchSize).withIndex()) {
                //Discard compounds without CAS Entry
                val entries = batch.filter { it.values.all { it != null } }.map {
                    val compound = ChemicalsDBEntry(it, fileType)
//                    val spread = (listOf(compound.CAS) zip compound.name).mapNotNull { (cas, iupac) ->
//                        CAS.fromString(cas)?.let { validCas ->
//                            ChemicalsDBEntry()
//                        }
                    compound
                }.filter{CAS.fromString(it.CAS) != null}
                Chemical.batchInsert(entries, ignore = false) { entry ->
                    this[Chemical.id] = entry.id
                    this[Chemical.cas] = CAS.fromString(entry.CAS)!!.toCASString()
                    this[Chemical.smiles] = entry?.smiles
                    this[Chemical.inchiKey] = entry.inchiKey
                    this[Chemical.inchi] = entry.inchi
                    this[Chemical.iupacName] = entry.name
                    this[Chemical.formula] = entry.formula
                }
                logger.info("Processing Batch ${index} with size ${batch.size}")
            }
        }
    }
}
