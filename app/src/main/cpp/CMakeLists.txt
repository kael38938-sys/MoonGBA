cmake_minimum_required(VERSION 3.10.2)
project(gba_core C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O2 -fvisibility=hidden -ffunction-sections -fdata-sections")
set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -Wl,--gc-sections")

add_library(gba_core SHARED
    jni_bridge.c
    cpu.c
    memory.c
)

find_library(log-lib log)
target_link_libraries(gba_core ${log-lib})
