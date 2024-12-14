import java.io.*;
import java.nio.file.*;
import java.util.*;

public class UVMAssemblerInterpreter {

    // Размер памяти и количество регистров для виртуальной машины
    private static final int MEMORY_SIZE = 1024;
    private static final int REGISTER_COUNT = 32;

    // Массив для хранения памяти и регистры виртуальной машины
    private byte[] memory = new byte[MEMORY_SIZE];
    private int[] registers = new int[REGISTER_COUNT];

    public static void main(String[] args) throws IOException {
        // Проверка, что аргументов командной строки достаточно
        if (args.length < 3) {
            System.out.println("Usage: java UVMAssemblerInterpreter <sourceFile> <binaryFile> <logFile>");
            return;  // Программа завершится, если недостаточно аргументов
        }

        String sourceFile = args[0];  // Путь к исходному ассемблерному файлу
        String binaryFile = args[1];  // Путь для сохранения бинарного файла
        String logFile = args[2];     // Путь для лог-файла

        // Сборка исходного ассемблерного кода в бинарный файл
        assemble(sourceFile, binaryFile, logFile);

        // Запуск интерпретатора для выполнения бинарного файла
        new UVMAssemblerInterpreter().interpret(binaryFile, "output.csv", 0, MEMORY_SIZE - 1);
    }

    // Метод для сборки исходного ассемблерного кода в бинарный файл
    public static void assemble(String sourceFile, String binaryFile, String logFile) throws IOException {
        // Чтение всех строк из исходного файла
        List<String> lines = Files.readAllLines(Paths.get(sourceFile));
        ByteArrayOutputStream binaryOutput = new ByteArrayOutputStream();  // Поток для хранения бинарных данных
        List<String> logLines = new ArrayList<>();  // Список для логирования команд

        // Проход по всем строкам исходного файла
        for (String line : lines) {
            String[] parts = line.trim().split(" ");  // Разделение строки на части
            if (parts.length == 0 || parts[0].startsWith("#")) continue;  // Пропуск пустых строк и комментариев

            // Разбор и сборка инструкции
            int opcode = Integer.parseInt(parts[0]);  // Получаем код операции
            byte[] instruction = new byte[5];  // Массив для хранения инструкции (5 байт)
            instruction[0] = (byte) ((opcode << 3) | Integer.parseInt(parts[1]));  // Заполнение первого байта

            // Заполнение оставшихся байтов на основе типа команды
            for (int i = 2; i < parts.length; i++) {
                int value = Integer.parseInt(parts[i]);
                for (int b = 0; b < 4; b++) {
                    instruction[i - 1] |= (byte) ((value >> (8 * b)) & 0xFF);  // Записываем данные в байты
                }
            }

            binaryOutput.write(instruction);  // Записываем инструкцию в бинарный поток

            // Логируем собранную команду в текстовом виде
            logLines.add(String.join(",", parts));
        }

        // Запись бинарных данных и логов в файлы
        Files.write(Paths.get(binaryFile), binaryOutput.toByteArray());
        Files.write(Paths.get(logFile), logLines);
    }

    // Метод для интерпретации бинарного файла и выполнения команд
    public void interpret(String binaryFile, String resultFile, int memoryStart, int memoryEnd) throws IOException {
        // Чтение бинарного файла
        byte[] binary = Files.readAllBytes(Paths.get(binaryFile));

        int pc = 0; // Счетчик команд (Program Counter)

        // Обработка инструкций до конца бинарного файла
        while (pc < binary.length) {
            int opcode = (binary[pc] >> 3) & 0x07;  // Извлечение кода операции
            int a = binary[pc] & 0x07;  // Извлечение регистра a
            int b = binary[pc + 1] & 0x1F;  // Извлечение регистра b

            // Обработка каждой команды в зависимости от кода операции
            switch (opcode) {
                case 1:  // Загрузка константы
                    int constant = ((binary[pc + 2] & 0xFF) << 16) |
                            ((binary[pc + 3] & 0xFF) << 8) |
                            (binary[pc + 4] & 0xFF);
                    registers[b] = constant;  // Сохраняем константу в регистр
                    break;

                case 2:  // Чтение из памяти
                    int addr = registers[b] + (binary[pc + 2] & 0xFF);  // Адрес в памяти
                    registers[a] = memory[addr] & 0xFF;  // Загружаем значение из памяти в регистр
                    break;

                case 0:  // Запись в память
                    memory[registers[b]] = (byte) registers[a];  // Записываем значение регистра в память
                    break;

                case 3:  // Операция бинарного сравнения (!=)
                    int op1 = registers[a];
                    int op2 = memory[registers[b] + (binary[pc + 2] & 0xFF)];
                    registers[a] = (op1 != op2) ? 1 : 0;  // Если значения не равны, то регистр принимает 1, иначе 0
                    break;

                default:
                    throw new IllegalArgumentException("Unknown opcode: " + opcode);  // Обработка неизвестных команд
            }

            pc += 5; // Переходим к следующей инструкции (каждая инструкция занимает 5 байт)
        }

        // Запись результатов работы в файл
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(resultFile))) {
            writer.write("Address,Value\n");  // Заголовок для CSV
            for (int i = memoryStart; i <= memoryEnd; i++) {
                writer.write(i + "," + (memory[i] & 0xFF) + "\n");  // Записываем состояние памяти в формате CSV
            }
        }
    }
}
