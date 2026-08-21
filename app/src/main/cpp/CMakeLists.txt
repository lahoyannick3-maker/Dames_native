cmake_minimum_required(VERSION 3.22.1)
project("damesnative")

add_library(
    damesnative
    SHARED
    native-lib.cpp
    moteur.c
)

find_library(log-lib log)

target_link_libraries(
    damesnative
    ${log-lib}
)
