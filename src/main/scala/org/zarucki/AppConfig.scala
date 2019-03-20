package org.zarucki

case class AppConfig(
    httpRestApiPort: Int,
    sessionSecret64CharacterLong: String,
    sessionHeaderName: String,
    boardXSize: Int,
    boardYSize: Int
)
