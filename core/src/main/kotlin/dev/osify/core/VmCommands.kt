package dev.osify.core

/** All shell values are single-quoted. No user-supplied engine flags are accepted. */
object VmCommands {
    fun quote(value: String): String {require(!value.contains('\u0000'));return "'"+value.replace("'","'\"'\"'")+"'"}
    const val prepare="set -eu\npkg update -y\npkg install -y qemu-system-x86-64-headless qemu-utils socat curl proot-distro swtpm\nqemu-system-x86_64 --version\nqemu-img --version"
    fun launch(id: String,url: String,sha: String,ram: Int,cores: Int,disk: Int,port: Int,password: String,firmware: String="BIOS",bootIso: Boolean=true): String {
        require(id.matches(Regex("[a-zA-Z0-9-]{1,64}")))
        require(sha.matches(Regex("[0-9a-f]{64}")))
        require(ram in 256..4096 && cores in 1..4 && disk in 2..128 && port in 5901..65535)
        require(url.startsWith("http://127.0.0.1:"))
        require(firmware in setOf("BIOS","UEFI"))
        require(password.matches(Regex("[a-zA-Z0-9]{8}")))
        return """
            set -eu
            command -v qemu-system-x86_64 >/dev/null || { echo 'Install the runtime from Launcher first' >&2; exit 10; }
            VM_ROOT="${'$'}PREFIX/var/lib/osify/$id"
            mkdir -p "${'$'}VM_ROOT"
            if [ -f "${'$'}VM_ROOT/qemu.pid" ] && kill -0 "${'$'}(cat "${'$'}VM_ROOT/qemu.pid")" 2>/dev/null && tr '\000' ' ' < "/proc/${'$'}(cat "${'$'}VM_ROOT/qemu.pid")/cmdline" | grep -Fq -- "${'$'}VM_ROOT/system.qcow2"; then
                echo 'This VM is already running; open its screen'; exit 0
            fi
            if [ ${quote(if(bootIso) "iso" else "disk")} = iso ] && [ ! -f "${'$'}VM_ROOT/install.iso" ]; then
                curl --fail --show-error --retry 1 --output "${'$'}VM_ROOT/import.tmp" ${quote(url)}
                printf '%s  %s\n' ${quote(sha)} "${'$'}VM_ROOT/import.tmp" | sha256sum --check --status
                mv "${'$'}VM_ROOT/import.tmp" "${'$'}VM_ROOT/install.iso"
            fi
            test -f "${'$'}VM_ROOT/system.qcow2" || qemu-img create -f qcow2 "${'$'}VM_ROOT/system.qcow2" ${disk}G
            printf '%s' ${quote(password)} > "${'$'}VM_ROOT/vnc.secret"
            chmod 600 "${'$'}VM_ROOT/vnc.secret"
            rm -f "${'$'}VM_ROOT/qmp.sock"
            FIRMWARE=(-machine pc)
            if [ ${quote(firmware)} = UEFI ]; then
                test -f "${'$'}PREFIX/share/qemu/edk2-x86_64-code.fd" || { echo 'UEFI firmware is missing; prepare runtime again' >&2; exit 14; }
                test -f "${'$'}VM_ROOT/uefi-vars.fd" || cp "${'$'}PREFIX/share/qemu/edk2-i386-vars.fd" "${'$'}VM_ROOT/uefi-vars.fd"
                mkdir -p "${'$'}VM_ROOT/tpm"
                rm -f "${'$'}VM_ROOT/tpm.sock"
                swtpm socket --tpm2 --terminate --tpmstate "dir=${'$'}VM_ROOT/tpm" --ctrl "type=unixio,path=${'$'}VM_ROOT/tpm.sock" --daemon
                FIRMWARE=(-machine q35 -drive "if=pflash,unit=0,format=raw,readonly=on,file=${'$'}PREFIX/share/qemu/edk2-x86_64-code.fd" -drive "if=pflash,unit=1,format=raw,file=${'$'}VM_ROOT/uefi-vars.fd" -chardev "socket,id=chrtpm,path=${'$'}VM_ROOT/tpm.sock" -tpmdev emulator,id=tpm0,chardev=chrtpm -device tpm-tis,tpmdev=tpm0)
            fi
            MEDIA=()
            if [ ${quote(if(bootIso) "iso" else "disk")} = iso ]; then MEDIA=(-cdrom "${'$'}VM_ROOT/install.iso"); fi
            qemu-system-x86_64 "${'$'}{FIRMWARE[@]}" -accel tcg,thread=multi -cpu max -smp $cores -m $ram \
                -drive "file=${'$'}VM_ROOT/system.qcow2,format=qcow2,if=ide" "${'$'}{MEDIA[@]}" \
                -boot menu=on,order=${if(bootIso) "dc" else "c"} -vga std -device usb-tablet -usb -device usb-kbd \
                -netdev user,id=n0 -device e1000,netdev=n0,romfile= -rtc base=localtime \
                -object "secret,id=vncpass,file=${'$'}VM_ROOT/vnc.secret" -vnc 127.0.0.1:${port-5900},password-secret=vncpass \
                -qmp "unix:${'$'}VM_ROOT/qmp.sock,server=on,wait=off" -monitor none \
                -pidfile "${'$'}VM_ROOT/qemu.pid" -daemonize 2> "${'$'}VM_ROOT/qemu-error.log" || { cat "${'$'}VM_ROOT/qemu-error.log" >&2; exit 11; }
            echo 'QEMU process started. Guest boot must be checked in the screen.'
        """.trimIndent()
    }
    fun control(id: String,action: String): String {
        require(id.matches(Regex("[a-zA-Z0-9-]{1,64}")))
        val command=when(action) {"shutdown"->"system_powerdown";"force"->"quit";"pause"->"stop";"resume"->"cont";else->error("Unsupported VM action")}
        return """
            set -eu
            VM_ROOT="${'$'}PREFIX/var/lib/osify/$id"
            test -S "${'$'}VM_ROOT/qmp.sock" || { echo 'VM monitor is unavailable; process may have exited' >&2; exit 12; }
            RESPONSE=${'$'}(printf '%s\n' '{"execute":"qmp_capabilities"}' '{"execute":"$command"}' | socat -t2 - "UNIX-CONNECT:${'$'}VM_ROOT/qmp.sock" || true)
            printf '%s\n' "${'$'}RESPONSE"
            if printf '%s' "${'$'}RESPONSE" | grep -q '"error"'; then exit 13; fi
            test "${'$'}(printf '%s' "${'$'}RESPONSE" | grep -o '"return"' | wc -l)" -ge 2
        """.trimIndent()
    }
}
