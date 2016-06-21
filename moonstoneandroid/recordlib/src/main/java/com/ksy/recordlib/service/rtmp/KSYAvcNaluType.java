package com.ksy.recordlib.service.rtmp;

/**
 * Table 7-1 – NAL unit type codes, syntax element categories, and NAL unit type
 * classes H.264-AVC-ISO_IEC_14496-10-2012.pdf, page 83.
 */
public class KSYAvcNaluType {

	// Unspecified
	public final static int Reserved = 0;

	// Coded slice of a non-IDR picture
	// slice_layer_without_partitioning_rbsp( )
	public final static int NonIDR = 1;
	// Coded slice data partition A slice_data_partition_a_layer_rbsp( )
	public final static int DataPartitionA = 2;
	// Coded slice data partition B slice_data_partition_b_layer_rbsp( )
	public final static int DataPartitionB = 3;
	// Coded slice data partition C slice_data_partition_c_layer_rbsp( )
	public final static int DataPartitionC = 4;
	// Coded slice of an IDR picture slice_layer_without_partitioning_rbsp(
	// )
	public final static int IDR = 5;
	// Supplemental enhancement information (SEI) sei_rbsp( )
	public final static int SEI = 6;
	// Sequence parameter set seq_parameter_set_rbsp( )
	public final static int SPS = 7;
	// Picture parameter set pic_parameter_set_rbsp( )
	public final static int PPS = 8;
	// Access unit delimiter access_unit_delimiter_rbsp( )
	public final static int AccessUnitDelimiter = 9;
	// End of sequence end_of_seq_rbsp( )
	public final static int EOSequence = 10;
	// End of stream end_of_stream_rbsp( )
	public final static int EOStream = 11;
	// Filler data filler_data_rbsp( )
	public final static int FilterData = 12;
	// Sequence parameter set extension seq_parameter_set_extension_rbsp( )
	public final static int SPSExt = 13;
	// Prefix NAL unit prefix_nal_unit_rbsp( )
	public final static int PrefixNALU = 14;
	// Subset sequence parameter set subset_seq_parameter_set_rbsp( )
	public final static int SubsetSPS = 15;
	// Coded slice of an auxiliary coded picture without partitioning
	// slice_layer_without_partitioning_rbsp( )
	public final static int LayerWithoutPartition = 19;
	// Coded slice extension slice_layer_extension_rbsp( )
	public final static int CodedSliceExt = 20;

}
