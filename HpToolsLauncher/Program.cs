/*
 * Certain versions of software and/or documents ("Material") accessible here may contain branding from
 * Hewlett-Packard Company (now HP Inc.) and Hewlett Packard Enterprise Company.  As of September 1, 2017,
 * the Material is now offered by Micro Focus, a separately owned and operated company.  Any reference to the HP
 * and Hewlett Packard Enterprise/HPE marks is historical in nature, and the HP and Hewlett Packard Enterprise/HPE
 * marks are the property of their respective owners.
 * __________________________________________________________________
 * MIT License
 *
 * (c) Copyright 2012-2023 Micro Focus or one of its affiliates.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software,
 * and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 * ___________________________________________________________________
 */

using HpToolsLauncher.Properties;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;

namespace HpToolsLauncher
{
    public enum TestStorageType
    {
        Alm,
        AlmLabManagement,
        FileSystem,
        LoadRunner,
        MBT,
        Unknown
    }

    static class Program
    {
        private static readonly Dictionary<string, string> argsDictionary = new Dictionary<string, string>();
        private static Launcher _apiRunner;

        //[MTAThread]
        static void Main(string[] args)
        {
            ConsoleWriter.WriteLine(Resources.GeneralStarted);
            ConsoleQuickEdit.Disable();
            Console.OutputEncoding = Encoding.UTF8;
            if (!args.Any() || args.Contains("/?"))
            {
                ShowHelp();
                return;
            }
            for (int i = 0; i < args.Count(); i += 2)
            {
                string key = args[i].StartsWith("-") ? args[i].Substring(1) : args[i];
                string val = i + 1 < args.Count() ? args[i + 1].Trim() : string.Empty;
                argsDictionary[key] = val;
            }
            string runtype, paramFileName, outEncoding;
            TestStorageType enmRuntype;
            argsDictionary.TryGetValue("runtype", out runtype);
            argsDictionary.TryGetValue("paramfile", out paramFileName);
            argsDictionary.TryGetValue("encoding", out outEncoding);
            if (!Enum.TryParse(runtype, true, out enmRuntype))
                enmRuntype = TestStorageType.Unknown;

            if (string.IsNullOrEmpty(paramFileName))
            {
                ShowHelp();
                return;
            }

            if (!string.IsNullOrWhiteSpace(outEncoding))
            {
                try
                {
                    Console.OutputEncoding = Encoding.GetEncoding(outEncoding);
                }
                catch
                {
                    Console.WriteLine("Unsupported encoding {0}. In this case UTF-8 will be used.", outEncoding);
                }
            }

            Console.CancelKeyPress += Console_CancelKeyPress;

            _apiRunner = new Launcher(paramFileName, enmRuntype, outEncoding);
            _apiRunner.Run();
        }

        private static void Console_CancelKeyPress(object sender, ConsoleCancelEventArgs e)
        {
            e.Cancel = true;
            _apiRunner.SafelyCancel();
        }

        private static void ShowHelp()
        {
            Console.WriteLine("Micro Focus Automation Tools Command Line Executer");
            Console.WriteLine();
            Console.Write("Usage: HpToolsLauncher.exe");
            Console.Write("  -paramfile ");
            Console.ForegroundColor = ConsoleColor.Cyan;
            Console.Write("<a file in key=value format> ");
            Console.ResetColor();
            Console.Write("  -encoding ");
            Console.ForegroundColor = ConsoleColor.Cyan;
            Console.Write("ASCII | UTF-7 | UTF-8 | UTF-16");
            Console.ResetColor();
            Console.WriteLine();
            Console.WriteLine();
            Console.WriteLine("-paramfile is required in for the program to run");
            Console.WriteLine("the parameter file may contain the following fields:");
            Console.WriteLine("\trunType=<Alm/FileSystem/LoadRunner>");
            Console.WriteLine("\talmServerUrl=http://<server>:<port>/qcbin");
            Console.WriteLine("\talmUserName=<user>");
            Console.WriteLine("\talmPassword=<password>");
            Console.WriteLine("\talmDomain=<domain>");
            Console.WriteLine("\talmProject=<project>");
            Console.WriteLine("\talmRunMode=<RUN_LOCAL/RUN_REMOTE/RUN_PLANNED_HOST>");
            Console.WriteLine("\talmTimeout=<-1>/<numberOfSeconds>");
            Console.WriteLine("\talmRunHost=<hostname>");
            Console.WriteLine("\tTestSet<number starting at 1>=<testSet>/<AlmFolder>");
            Console.WriteLine("\tTest<number starting at 1>=<testFolderPath>/<a Path ContainingTestFolders>/<mtbFilePath>");
            Console.WriteLine("* the last two fields may recur more than once with different index numbers");
            Console.WriteLine();
            Console.WriteLine("-encoding is optional and can take one of the values: ASCII, UTF-7, UTF-8 or UTF-16");

            Environment.Exit((int)Launcher.ExitCodeEnum.Failed);
        }
    }
}
