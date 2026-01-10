package tj.app.quran_todo.data.database

import tj.app.quran_todo.data.database.entity.quran.Sajda
import tj.app.quran_todo.data.database.entity.type_converter.BaseTypeConverter

class SajdaConverter : BaseTypeConverter<Sajda>(Sajda.serializer())
