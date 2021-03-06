/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.table.functions.python;

import org.apache.flink.fnexecution.v1.FlinkFnApi;
import org.apache.flink.table.functions.python.coders.RowCoder;
import org.apache.flink.table.types.logical.BigIntType;
import org.apache.flink.table.types.logical.RowType;
import org.apache.flink.types.Row;

import org.apache.beam.runners.fnexecution.control.JobBundleFactory;
import org.apache.beam.runners.fnexecution.control.RemoteBundle;
import org.apache.beam.runners.fnexecution.control.StageBundleFactory;
import org.apache.beam.sdk.coders.Coder;
import org.apache.beam.sdk.coders.VarLongCoder;
import org.apache.beam.sdk.fn.data.FnDataReceiver;
import org.apache.beam.sdk.transforms.windowing.BoundedWindow;
import org.apache.beam.sdk.transforms.windowing.GlobalWindow;
import org.apache.beam.sdk.transforms.windowing.PaneInfo;
import org.apache.beam.sdk.util.WindowedValue;
import org.apache.beam.vendor.grpc.v1p21p0.com.google.protobuf.Struct;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link PythonScalarFunctionRunner}. These test that:
 *
 * <ul>
 *     <li>The input data type and output data type are properly constructed</li>
 *     <li>The UDF proto is properly constructed</li>
 * </ul>
 */
public class PythonScalarFunctionRunnerTest extends AbstractPythonScalarFunctionRunnerTest<Row, Row> {

	@Test
	public void testInputOutputDataTypeConstructedProperlyForSingleUDF() {
		final AbstractPythonScalarFunctionRunner<Row, Row> runner = createSingleUDFRunner();

		// check input coder
		Coder<Row> inputCoder = runner.getInputCoder();
		assertTrue(inputCoder instanceof RowCoder);

		Coder<?>[] inputFieldCoders = ((RowCoder) inputCoder).getFieldCoders();
		assertEquals(1, inputFieldCoders.length);
		assertTrue(inputFieldCoders[0] instanceof VarLongCoder);

		// check output coder
		Coder<Row> outputCoder = runner.getOutputCoder();
		assertTrue(outputCoder instanceof RowCoder);
		Coder<?>[] outputFieldCoders = ((RowCoder) outputCoder).getFieldCoders();
		assertEquals(1, outputFieldCoders.length);
		assertTrue(outputFieldCoders[0] instanceof VarLongCoder);
	}

	@Test
	public void testInputOutputDataTypeConstructedProperlyForMultipleUDFs() {
		final AbstractPythonScalarFunctionRunner<Row, Row> runner = createMultipleUDFRunner();

		// check input coder
		Coder<Row> inputCoder = runner.getInputCoder();
		assertTrue(inputCoder instanceof RowCoder);

		Coder<?>[] inputFieldCoders = ((RowCoder) inputCoder).getFieldCoders();
		assertEquals(3, inputFieldCoders.length);
		assertTrue(inputFieldCoders[0] instanceof VarLongCoder);
		assertTrue(inputFieldCoders[1] instanceof VarLongCoder);
		assertTrue(inputFieldCoders[2] instanceof VarLongCoder);

		// check output coder
		Coder<Row> outputCoder = runner.getOutputCoder();
		assertTrue(outputCoder instanceof RowCoder);
		Coder<?>[] outputFieldCoders = ((RowCoder) outputCoder).getFieldCoders();
		assertEquals(2, outputFieldCoders.length);
		assertTrue(outputFieldCoders[0] instanceof VarLongCoder);
		assertTrue(outputFieldCoders[1] instanceof VarLongCoder);
	}

	@Test
	public void testInputOutputDataTypeConstructedProperlyForChainedUDFs() {
		final AbstractPythonScalarFunctionRunner<Row, Row> runner = createChainedUDFRunner();

		// check input coder
		Coder<Row> inputCoder = runner.getInputCoder();
		assertTrue(inputCoder instanceof RowCoder);

		Coder<?>[] inputFieldCoders = ((RowCoder) inputCoder).getFieldCoders();
		assertEquals(5, inputFieldCoders.length);
		assertTrue(inputFieldCoders[0] instanceof VarLongCoder);
		assertTrue(inputFieldCoders[1] instanceof VarLongCoder);
		assertTrue(inputFieldCoders[2] instanceof VarLongCoder);
		assertTrue(inputFieldCoders[3] instanceof VarLongCoder);
		assertTrue(inputFieldCoders[4] instanceof VarLongCoder);

		// check output coder
		Coder<Row> outputCoder = runner.getOutputCoder();
		assertTrue(outputCoder instanceof RowCoder);
		Coder<?>[] outputFieldCoders = ((RowCoder) outputCoder).getFieldCoders();
		assertEquals(3, outputFieldCoders.length);
		assertTrue(outputFieldCoders[0] instanceof VarLongCoder);
		assertTrue(outputFieldCoders[1] instanceof VarLongCoder);
		assertTrue(outputFieldCoders[2] instanceof VarLongCoder);
	}

	@Test
	public void testUDFnProtoConstructedProperlyForSingleUDF() {
		final AbstractPythonScalarFunctionRunner<Row, Row> runner = createSingleUDFRunner();

		FlinkFnApi.UserDefinedFunctions udfs = runner.getUserDefinedFunctionsProto();
		assertEquals(1, udfs.getUdfsCount());

		FlinkFnApi.UserDefinedFunction udf = udfs.getUdfs(0);
		assertEquals(1, udf.getInputsCount());
		assertEquals(0, udf.getInputs(0).getInputOffset());
	}

	@Test
	public void testUDFProtoConstructedProperlyForMultipleUDFs() {
		final AbstractPythonScalarFunctionRunner<Row, Row> runner = createMultipleUDFRunner();

		FlinkFnApi.UserDefinedFunctions udfs = runner.getUserDefinedFunctionsProto();
		assertEquals(2, udfs.getUdfsCount());

		FlinkFnApi.UserDefinedFunction udf = udfs.getUdfs(0);
		assertEquals(2, udf.getInputsCount());
		assertEquals(0, udf.getInputs(0).getInputOffset());
		assertEquals(1, udf.getInputs(1).getInputOffset());

		udf = udfs.getUdfs(1);
		assertEquals(2, udf.getInputsCount());
		assertEquals(0, udf.getInputs(0).getInputOffset());
		assertEquals(2, udf.getInputs(1).getInputOffset());
	}

	@Test
	public void testUDFProtoConstructedProperlyForChainedUDFs() {
		final AbstractPythonScalarFunctionRunner<Row, Row> runner = createChainedUDFRunner();

		FlinkFnApi.UserDefinedFunctions udfs = runner.getUserDefinedFunctionsProto();
		assertEquals(3, udfs.getUdfsCount());

		FlinkFnApi.UserDefinedFunction udf = udfs.getUdfs(0);
		assertEquals(2, udf.getInputsCount());
		assertEquals(0, udf.getInputs(0).getInputOffset());
		assertEquals(1, udf.getInputs(1).getInputOffset());

		udf = udfs.getUdfs(1);
		assertEquals(2, udf.getInputsCount());
		assertEquals(0, udf.getInputs(0).getInputOffset());
		FlinkFnApi.UserDefinedFunction chainedUdf = udf.getInputs(1).getUdf();
		assertEquals(2, chainedUdf.getInputsCount());
		assertEquals(1, chainedUdf.getInputs(0).getInputOffset());
		assertEquals(2, chainedUdf.getInputs(1).getInputOffset());

		udf = udfs.getUdfs(2);
		chainedUdf = udf.getInputs(0).getUdf();
		assertEquals(2, chainedUdf.getInputsCount());
		assertEquals(1, chainedUdf.getInputs(0).getInputOffset());
		assertEquals(3, chainedUdf.getInputs(1).getInputOffset());
		chainedUdf = udf.getInputs(1).getUdf();
		assertEquals(2, chainedUdf.getInputsCount());
		assertEquals(3, chainedUdf.getInputs(0).getInputOffset());
		assertEquals(4, chainedUdf.getInputs(1).getInputOffset());
	}

	@Test
	public void testPythonScalarFunctionRunner() throws Exception {
		JobBundleFactory jobBundleFactorySpy = spy(JobBundleFactory.class);
		FnDataReceiver<Row> resultReceiverSpy = spy(FnDataReceiver.class);
		final AbstractPythonScalarFunctionRunner<Row, Row> runner =
			createUDFRunner(jobBundleFactorySpy, resultReceiverSpy);

		StageBundleFactory stageBundleFactorySpy = spy(StageBundleFactory.class);
		when(jobBundleFactorySpy.forStage(any())).thenReturn(stageBundleFactorySpy);
		RemoteBundle remoteBundleSpy = spy(RemoteBundle.class);
		when(stageBundleFactorySpy.getBundle(any(), any(), any())).thenReturn(remoteBundleSpy);
		Map<String, FnDataReceiver<WindowedValue<?>>> inputReceivers = new HashMap<>();
		FnDataReceiver<WindowedValue<?>> windowedValueReceiverSpy = spy(FnDataReceiver.class);
		inputReceivers.put("input", windowedValueReceiverSpy);
		when(remoteBundleSpy.getInputReceivers()).thenReturn(inputReceivers);

		runner.open();
		verify(jobBundleFactorySpy, times(1)).forStage(any());

		// verify stageBundleFactory.getBundle is called during startBundle
		verify(stageBundleFactorySpy, times(0)).getBundle(any(), any(), any());
		runner.startBundle();
		verify(stageBundleFactorySpy, times(1)).getBundle(any(), any(), any());

		// verify input element is hand over to input receiver
		runner.processElement(Row.of(1L));
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		runner.getInputCoder().encode(Row.of(1L), baos);
		verify(windowedValueReceiverSpy, times(1)).accept(argThat(
			windowedValue ->
				windowedValue.getWindows().equals(Collections.singletonList(GlobalWindow.INSTANCE)) &&
				windowedValue.getPane() == PaneInfo.NO_FIRING &&
				windowedValue.getTimestamp() == BoundedWindow.TIMESTAMP_MIN_VALUE &&
				Objects.deepEquals(windowedValue.getValue(), baos.toByteArray())));

		// verify remoteBundle.close() is called during finishBundle
		verify(remoteBundleSpy, times(0)).close();
		runner.finishBundle();
		verify(remoteBundleSpy, times(1)).close();
	}

	@Override
	public AbstractPythonScalarFunctionRunner<Row, Row> createPythonScalarFunctionRunner(
		final PythonFunctionInfo[] pythonFunctionInfos,
		RowType inputType,
		RowType outputType) {
		final FnDataReceiver<Row> dummyReceiver = input -> {
			// ignore the execution results
		};

		final PythonEnv pythonEnv = new PythonEnv("", "", PythonEnv.ExecType.PROCESS);

		return new PythonScalarFunctionRunner(
			"testPythonRunner",
			dummyReceiver,
			pythonFunctionInfos,
			pythonEnv,
			inputType,
			outputType,
			System.getProperty("java.io.tmpdir"));
	}

	private AbstractPythonScalarFunctionRunner<Row, Row> createUDFRunner(
		JobBundleFactory jobBundleFactory, FnDataReceiver<Row> receiver) {
		PythonFunctionInfo[] pythonFunctionInfos = new PythonFunctionInfo[] {
			new PythonFunctionInfo(
				DummyPythonFunction.INSTANCE,
				new Integer[]{0})
		};

		RowType rowType = new RowType(Collections.singletonList(new RowType.RowField("f1", new BigIntType())));

		final PythonEnv pythonEnv = new PythonEnv("", "", PythonEnv.ExecType.PROCESS);

		return new PythonScalarFunctionRunnerTestHarness(
			"testPythonRunner",
			receiver,
			pythonFunctionInfos,
			pythonEnv,
			rowType,
			rowType,
			jobBundleFactory,
			System.getProperty("java.io.tmpdir"));
	}

	private static class PythonScalarFunctionRunnerTestHarness extends PythonScalarFunctionRunner {

		private final JobBundleFactory jobBundleFactory;

		PythonScalarFunctionRunnerTestHarness(
			String taskName,
			FnDataReceiver<Row> resultReceiver,
			PythonFunctionInfo[] scalarFunctions,
			PythonEnv pythonEnv,
			RowType inputType, RowType outputType,
			JobBundleFactory jobBundleFactory,
			String tempDir) {
			super(taskName, resultReceiver, scalarFunctions, pythonEnv, inputType, outputType, tempDir);
			this.jobBundleFactory = jobBundleFactory;
		}

		@Override
		public JobBundleFactory createJobBundleFactory(Struct pipelineOptions) throws Exception {
			return jobBundleFactory;
		}
	}
}
