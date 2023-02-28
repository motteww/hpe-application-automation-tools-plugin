﻿/*
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

using HP.LoadRunner.Interop.Wlrun;
using System;
using System.Text;

namespace HpToolsLauncher.TestRunners
{
    public class SummaryDataLogger
    {
        private bool m_logVusersStates;
        private bool m_logErrorCount;
        private bool m_logTransactionStatistics;
        private int m_pollingInterval;

        public SummaryDataLogger(bool logVusersStates, bool logErrorCount, bool logTransactionStatistics, int pollingInterval)
        {
            m_logVusersStates = logVusersStates;
            m_logErrorCount = logErrorCount;
            m_logTransactionStatistics = logTransactionStatistics;
            m_pollingInterval = pollingInterval;
        }

        public SummaryDataLogger() { }

        public int GetPollingInterval()
        {
            return m_pollingInterval * 1000;
        }

        private enum VUSERS_STATE
        {
            Down = 1,
            Pending = 2,
            Init = 3,
            Ready = 4,
            Run = 5,
            Rendez = 6,
            Passed = 7,
            Failed = 8,
            Error = 9,
            Exiting = 10,
            Stopped = 11,
            G_Exit = 12 //Gradual Exiting
        }

        private void LogVuserStates(LrScenario scenario)
        {
            StringBuilder headerBuilder = new StringBuilder(),
                          bodyBuilder = new StringBuilder();

            foreach (var vuserState in Enum.GetValues(typeof(VUSERS_STATE)))
            {
                headerBuilder.Append(string.Format("{0, -10}", vuserState.ToString()));
            }

            foreach (var vuserState in Enum.GetValues(typeof(VUSERS_STATE)))
            {
                bodyBuilder.Append(string.Format("{0, -10}", scenario.GetVusersCount((int)vuserState)));
            }

            ConsoleWriter.WriteLine(headerBuilder.ToString());
            ConsoleWriter.WriteLine(bodyBuilder.ToString());
        }

        private void LogErrorCount(LrScenario scenario)
        {

            int errorsCount = scenario.GetErrorsCount("");

            ConsoleWriter.WriteLine("Error count: " + errorsCount);
        }

        private void LogScenarioDuration(LrScenario scenario)
        {
            int scenarioDuration = scenario.ScenarioDuration;
            TimeSpan time = TimeSpan.FromSeconds(scenarioDuration);
            string convertedTime = time.ToString(@"dd\:hh\:mm\:ss");

            ConsoleWriter.WriteLine("Elapsed Time (D:H:M:S): " + convertedTime);
        }

        public void LogSummaryData(LrScenario scenario)
        {
            if (m_logVusersStates || m_logErrorCount || m_logTransactionStatistics)
            {
                LogScenarioDuration(scenario);

                if (m_logVusersStates)
                {
                    LogVuserStates(scenario);
                }

                if (m_logErrorCount)
                {
                    LogErrorCount(scenario);
                }

                if (m_logTransactionStatistics)
                {
                    LogTransactionStatistics(scenario);
                }
            }
        }

        public bool IsAnyDataLogged()
        {
            return (m_logVusersStates || m_logErrorCount || m_logTransactionStatistics);
        }

        private void LogTransactionStatistics(LrScenario scenario)
        {
            int passed = 0, failed = 0;
            double hitsPerSecond = 0;
            scenario.GetTransactionStatistics(ref passed, ref failed, ref hitsPerSecond);

            ConsoleWriter.WriteLine("Passed transactions: " + passed);
            ConsoleWriter.WriteLine("Failed transactions: " + failed);
            ConsoleWriter.WriteLine("Hits per second: " + Math.Round(hitsPerSecond, 2));
        }

        public void LogTransactionDetails(LrScenario scenario)
        {
            string transactionDetails;
            scenario.GetTransactionStatisticsDetails(out transactionDetails);

            ConsoleWriter.WriteLine("Transaction details: " + transactionDetails);
        }
    }
}
